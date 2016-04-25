package com.github.mtakaki.credentialstorage.hibernate;

import java.util.List;
import java.util.Map;
import java.util.SortedSet;

import javax.sql.DataSource;

import org.hibernate.SessionFactory;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;
import org.hibernate.engine.jdbc.connections.internal.DatasourceConnectionProviderImpl;
import org.hibernate.engine.jdbc.connections.spi.ConnectionProvider;
import org.hibernate.service.ServiceRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Sets;

import io.dropwizard.db.ManagedDataSource;
import io.dropwizard.db.PooledDataSourceFactory;
import io.dropwizard.setup.Environment;

public class SessionFactoryFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(SessionFactoryFactory.class);

    public SessionFactory build(final RemoteCredentialHibernateBundle<?> bundle,
            final Environment environment,
            final PooledDataSourceFactory dbConfig,
            final List<Class<?>> entities,
            final String name) {
        final ManagedDataSource dataSource = dbConfig.build(environment.metrics(), name);
        return this.build(bundle, environment, dbConfig, dataSource, entities);
    }

    public SessionFactory build(final RemoteCredentialHibernateBundle<?> bundle,
            final Environment environment,
            final PooledDataSourceFactory dbConfig,
            final ManagedDataSource dataSource,
            final List<Class<?>> entities) {
        final ConnectionProvider provider = this.buildConnectionProvider(dataSource,
                dbConfig.getProperties());
        final SessionFactory factory = this.buildSessionFactory(bundle,
                dbConfig,
                provider,
                dbConfig.getProperties(),
                entities);
        final SessionFactoryManager managedFactory = new SessionFactoryManager(factory, dataSource);
        environment.lifecycle().manage(managedFactory);
        return factory;
    }

    private ConnectionProvider buildConnectionProvider(final DataSource dataSource,
            final Map<String, String> properties) {
        final DatasourceConnectionProviderImpl connectionProvider = new DatasourceConnectionProviderImpl();
        connectionProvider.setDataSource(dataSource);
        connectionProvider.configure(properties);
        return connectionProvider;
    }

    private SessionFactory buildSessionFactory(final RemoteCredentialHibernateBundle<?> bundle,
            final PooledDataSourceFactory dbConfig,
            final ConnectionProvider connectionProvider,
            final Map<String, String> properties,
            final List<Class<?>> entities) {
        final Configuration configuration = new Configuration();
        configuration.setProperty(AvailableSettings.CURRENT_SESSION_CONTEXT_CLASS, "managed");
        configuration.setProperty(AvailableSettings.USE_SQL_COMMENTS,
                Boolean.toString(dbConfig.isAutoCommentsEnabled()));
        configuration.setProperty(AvailableSettings.USE_GET_GENERATED_KEYS, "true");
        configuration.setProperty(AvailableSettings.GENERATE_STATISTICS, "true");
        configuration.setProperty(AvailableSettings.USE_REFLECTION_OPTIMIZER, "true");
        configuration.setProperty(AvailableSettings.ORDER_UPDATES, "true");
        configuration.setProperty(AvailableSettings.ORDER_INSERTS, "true");
        configuration.setProperty(AvailableSettings.USE_NEW_ID_GENERATOR_MAPPINGS, "true");
        configuration.setProperty("jadira.usertype.autoRegisterUserTypes", "true");
        for (final Map.Entry<String, String> property : properties.entrySet()) {
            configuration.setProperty(property.getKey(), property.getValue());
        }

        this.addAnnotatedClasses(configuration, entities);

        final ServiceRegistry registry = new StandardServiceRegistryBuilder()
                .addService(ConnectionProvider.class, connectionProvider)
                .applySettings(properties)
                .build();

        this.configure(configuration, registry);

        return configuration.buildSessionFactory(registry);
    }

    protected void configure(final Configuration configuration, final ServiceRegistry registry) {
    }

    private void addAnnotatedClasses(final Configuration configuration,
            final Iterable<Class<?>> entities) {
        final SortedSet<String> entityClasses = Sets.newTreeSet();
        for (final Class<?> klass : entities) {
            configuration.addAnnotatedClass(klass);
            entityClasses.add(klass.getCanonicalName());
        }
        LOGGER.info("Entity classes: {}", entityClasses);
    }
}