package com.github.mtakaki.credentialstorage.hibernate;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;

import org.hibernate.SessionFactory;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.datatype.hibernate4.Hibernate4Module;
import com.github.mtakaki.credentialstorage.client.CredentialStorageServiceClient;
import com.github.mtakaki.credentialstorage.client.model.Credential;
import com.google.common.collect.ImmutableList;

import io.dropwizard.Configuration;
import io.dropwizard.ConfiguredBundle;
import io.dropwizard.db.DatabaseConfiguration;
import io.dropwizard.db.ManagedDataSource;
import io.dropwizard.db.PooledDataSourceFactory;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.dropwizard.util.Duration;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class RemoteCredentialHibernateBundle<T extends Configuration>
        implements ConfiguredBundle<T>, DatabaseConfiguration<T> {
    public static final String DEFAULT_NAME = "hibernate";

    private final ImmutableList<Class<?>> entities;
    private final SessionFactoryFactory sessionFactoryFactory;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private final AtomicReference<SessionFactory> sessionFactory = new AtomicReference<>();

    private Environment environment;

    private Credential credential;
    private CredentialStorageServiceClient client;

    @Getter
    private SessionHolders sessionHolders;
    private ManagedDataSource dataSource;
    private RemoteCredentialDataSourceFactory dataSourceFactory;
    private final ThreadLocal<SessionFactory> localSessionFactory = new ThreadLocal<>();

    protected RemoteCredentialHibernateBundle(final Class<?> entity, final Class<?>... entities) {
        this.entities = ImmutableList.<Class<?>> builder().add(entity).add(entities).build();
        this.sessionFactoryFactory = new SessionFactoryFactory();
    }

    /**
     * Gets the {@link SessionFactory} and sets it to the local thread.
     *
     * @return The current {@link SessionFactory}.
     */
    public SessionFactory getSessionFactory() {
        final SessionFactory sessionFactory = this.sessionFactory.get();
        this.localSessionFactory.set(sessionFactory);
        return sessionFactory;
    }

    private void createDataSourceAndSessionFactory(final MetricRegistry metricRegistry) {
        // The credential retrieval needs to be enabled to override the
        // settings.
        if (this.dataSourceFactory.isRetrieveCredentials()) {
            this.dataSourceFactory.setUser(this.credential.getPrimary());
            this.dataSourceFactory.setPassword(
                    this.dataSourceFactory.getUser() != null
                            && this.credential.getSecondary() == null
                                    ? ""
                                    : this.credential.getSecondary());
        }
        // Unregister metrics to we can wire the new data source into it.
        this.unregisterCurrentMetrics(metricRegistry);

        this.dataSource = this.dataSourceFactory.build(metricRegistry,
                this.name());
        try {
            this.dataSource.start();
        } catch (final Exception e) {
            throw new RuntimeException("Failed to initialize the data source.", e);
        }
        final SessionFactory sessionFactory = this.sessionFactoryFactory.build(this,
                this.environment, this.dataSourceFactory, this.dataSource, this.entities);
        this.sessionFactory.set(sessionFactory);

        this.sessionHolders = new SessionHolders(sessionFactory, this.dataSource);
    }

    private void unregisterCurrentMetrics(final MetricRegistry metricRegistry) {
        metricRegistry.remove(MetricRegistry.name(this.name(), "pool", "TotalConnections"));
        metricRegistry.remove(MetricRegistry.name(this.name(), "pool", "IdleConnections"));
        metricRegistry.remove(MetricRegistry.name(this.name(), "pool", "ActiveConnections"));
        metricRegistry.remove(MetricRegistry.name(this.name(), "pool", "PendingConnections"));
    }

    public SessionFactory getCurrentThreadSessionFactory() {
        return this.localSessionFactory.get();
    }

    public void setCurrentThreadSessionFactory(final SessionFactory sessionFactory) {
        this.localSessionFactory.set(sessionFactory);
    }

    private void scheduleCredentialRetrieval(
            final RemoteCredentialDataSourceFactory dataSourceFactory) {
        this.scheduler.scheduleAtFixedRate(() -> {
            try {
                log.info("Retrieving credentials.");
                final Credential newCredential = this.client.getCredential();
                // We only create a new connection if the credentials were
                // updated.
                if (!newCredential.equals(this.credential)) {
                    log.info("Credentials updated. Creating new connection.");
                    this.credential = newCredential;

                    final SessionHolders oldSessionHolders = this.sessionHolders;
                    this.sessionHolders.setCloseSession(true);

                    // TODO Need to figure out a way of registering the new
                    // datasource metrics.
                    // this.dataSource.setMetricRegistry(null);
                    this.createDataSourceAndSessionFactory(this.environment.metrics());

                    // If there's no active connection at the moment we can
                    // close the old connection right now. New requests will
                    // already use the new session factory.
                    if (oldSessionHolders.isEmpty()) {
                        oldSessionHolders.closeConnections();
                    }
                }
            } catch (final Exception e) {
                log.error("Failed to retrieve credentials. The credentials will not be updated.",
                        e);
            }
        }, 0L, dataSourceFactory.getRefreshFrequency(), TimeUnit.DAYS);
    }

    @Override
    public final void initialize(final Bootstrap<?> bootstrap) {
        bootstrap.getObjectMapper().registerModule(this.createHibernate4Module());
    }

    /**
     * Override to configure the {@link Hibernate4Module}.
     *
     * @return The created {@link Hibernate4Module}.
     */
    protected Hibernate4Module createHibernate4Module() {
        return new Hibernate4Module();
    }

    /**
     * Override to configure the name of the bundle (It's used for the bundle
     * health check and database pool metrics)
     *
     * @return The name of the bundle.
     */
    protected String name() {
        return DEFAULT_NAME;
    }

    @Override
    public final void run(final T configuration, final Environment environment) throws Exception {
        this.environment = environment;

        final PooledDataSourceFactory dbConfig = this.getDataSourceFactory(configuration);
        this.registerUnitOfWorkListerIfAbsent(environment).registerBundle(this.name(), this);
        environment.healthChecks().register(this.name(),
                new SessionFactoryHealthCheck(
                        environment.getHealthCheckExecutorService(),
                        dbConfig.getValidationQueryTimeout().or(Duration.seconds(5)),
                        this.sessionFactory.get(),
                        dbConfig.getValidationQuery()));

        this.dataSourceFactory = (RemoteCredentialDataSourceFactory) this
                .getDataSourceFactory(configuration);
        // If the feature is disabled we don't need to create the client and
        // retrieve the credentials.
        if (this.dataSourceFactory.isRetrieveCredentials()) {
            try {
                this.client = this.createCredentialClient();
                this.credential = this.client.getCredential();
            } catch (NoSuchAlgorithmException | InvalidKeySpecException | IOException
                    | InvalidKeyException | NoSuchPaddingException | IllegalBlockSizeException
                    | BadPaddingException e) {
                throw new RuntimeException("Failed to initialize credential storage client.", e);
            }
        }
        this.createDataSourceAndSessionFactory(this.environment.metrics());
        // The scheduled credential retrieval is useless if the feature is
        // disabled.
        if (this.dataSourceFactory.isRetrieveCredentials()) {
            this.scheduleCredentialRetrieval(this.dataSourceFactory);
        }
    }

    /**
     * Creates the {@link CredentialStorageServiceClient} with the configuration
     * stored under {@link RemoteCredentialDataSourceFactory}.
     *
     * @return A {@link CredentialStorageServiceClient}.
     * @throws NoSuchAlgorithmException
     *             Thrown if the encryption/decryption algorithm is not
     *             available.
     * @throws InvalidKeySpecException
     *             If the private/public key are invalid.
     * @throws FileNotFoundException
     *             Thrown if the private/public key files were not available.
     * @throws IOException
     *             Thrown if the private/public key files could not be opened.
     */
    private CredentialStorageServiceClient createCredentialClient() throws NoSuchAlgorithmException,
            InvalidKeySpecException, FileNotFoundException, IOException {
        return new CredentialStorageServiceClient(
                new File(this.dataSourceFactory.getPrivateKeyFile()),
                new File(this.dataSourceFactory.getPublicKeyFile()),
                this.dataSourceFactory.getCredentialServiceURL(),
                this.dataSourceFactory.getCredentialClientConfiguration());
    }

    private UnitOfWorkApplicationListener registerUnitOfWorkListerIfAbsent(
            final Environment environment) {
        for (final Object singleton : environment.jersey().getResourceConfig().getSingletons()) {
            if (singleton instanceof UnitOfWorkApplicationListener) {
                return (UnitOfWorkApplicationListener) singleton;
            }
        }
        final UnitOfWorkApplicationListener listener = new UnitOfWorkApplicationListener();
        environment.jersey().register(listener);
        return listener;
    }
}