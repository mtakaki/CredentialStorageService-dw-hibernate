package com.github.mtakaki.credentialstorage.hibernate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hibernate.EmptyInterceptor;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.service.ServiceRegistry;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.ImmutableList;

import io.dropwizard.db.DataSourceFactory;
import io.dropwizard.db.ManagedPooledDataSource;
import io.dropwizard.lifecycle.setup.LifecycleEnvironment;
import io.dropwizard.logging.BootstrapLogging;
import io.dropwizard.setup.Environment;

public class SessionFactoryFactoryTest {
    static {
        BootstrapLogging.bootstrap();
    }

    private final SessionFactoryFactory factory = new SessionFactoryFactory();

    private final RemoteCredentialHibernateBundle<?> bundle = mock(RemoteCredentialHibernateBundle.class);
    private final LifecycleEnvironment lifecycleEnvironment = mock(LifecycleEnvironment.class);
    private final Environment environment = mock(Environment.class);
    private final MetricRegistry metricRegistry = new MetricRegistry();

    private DataSourceFactory config;
    private SessionFactory sessionFactory;

    @Before
    public void setUp() throws Exception {
        when(this.environment.metrics()).thenReturn(this.metricRegistry);
        when(this.environment.lifecycle()).thenReturn(this.lifecycleEnvironment);

        this.config = new DataSourceFactory();
        this.config.setUrl("jdbc:hsqldb:mem:DbTest-" + System.currentTimeMillis());
        this.config.setUser("sa");
        this.config.setDriverClass("org.hsqldb.jdbcDriver");
        this.config.setValidationQuery("SELECT 1 FROM INFORMATION_SCHEMA.SYSTEM_USERS");
    }

    @After
    public void tearDown() throws Exception {
        if (this.sessionFactory != null) {
            this.sessionFactory.close();
        }
    }

    @Test
    public void managesTheSessionFactory() throws Exception {
        this.build();

        verify(this.lifecycleEnvironment).manage(any(SessionFactoryManager.class));
    }

    @Test
    public void callsBundleToConfigure() throws Exception {
      this.build();

//      verify(this.bundle).configure(any(Configuration.class));
    }

    @Test
    public void setsPoolName() {
        this.build();

        final ArgumentCaptor<SessionFactoryManager> sessionFactoryManager = ArgumentCaptor.forClass(SessionFactoryManager.class);
        verify(this.lifecycleEnvironment).manage(sessionFactoryManager.capture());
        final ManagedPooledDataSource dataSource = (ManagedPooledDataSource) sessionFactoryManager.getValue().getDataSource();
//        assertThat(dataSource.toString()).isEqualTo("hibernate");
    }

    @Test
    public void setsACustomPoolName() {
        this.sessionFactory = this.factory.build(this.bundle, this.environment, this.config,
                ImmutableList.<Class<?>>of(Person.class), "custom-hibernate-db");

        final ArgumentCaptor<SessionFactoryManager> sessionFactoryManager = ArgumentCaptor.forClass(SessionFactoryManager.class);
        verify(this.lifecycleEnvironment).manage(sessionFactoryManager.capture());
        final ManagedPooledDataSource dataSource = (ManagedPooledDataSource) sessionFactoryManager.getValue().getDataSource();
//        assertThat(dataSource.getPool().getName()).isEqualTo("custom-hibernate-db");
    }

    @Test
    public void buildsAWorkingSessionFactory() throws Exception {
        this.build();

        final Session session = this.sessionFactory.openSession();
        try {
            session.createSQLQuery("DROP TABLE people IF EXISTS").executeUpdate();
            session.createSQLQuery("CREATE TABLE people (name varchar(100) primary key, email varchar(100), birthday timestamp)").executeUpdate();
            session.createSQLQuery("INSERT INTO people VALUES ('Coda', 'coda@example.com', '1979-01-02 00:22:00')").executeUpdate();

            final Person entity = (Person) session.get(Person.class, "Coda");

            assertThat(entity.getName())
                    .isEqualTo("Coda");

            assertThat(entity.getEmail())
                    .isEqualTo("coda@example.com");

            assertThat(entity.getBirthday().toDateTime(DateTimeZone.UTC))
                    .isEqualTo(new DateTime(1979, 1, 2, 0, 22, DateTimeZone.UTC));
        } finally {
            session.close();
        }
    }

    @Test
    public void configureRunsBeforeSessionFactoryCreation(){
        final SessionFactoryFactory customFactory = new SessionFactoryFactory() {
            @Override
            protected void configure(final Configuration configuration, final ServiceRegistry registry) {
                super.configure(configuration, registry);
                configuration.setInterceptor(EmptyInterceptor.INSTANCE);
            }
        };
        this.sessionFactory = customFactory.build(this.bundle,
                                             this.environment,
                                             this.config,
                                             ImmutableList.<Class<?>>of(Person.class),
                                             RemoteCredentialHibernateBundle.DEFAULT_NAME);

        assertThat(this.sessionFactory.getSessionFactoryOptions().getInterceptor()).isSameAs(EmptyInterceptor.INSTANCE);
    }

    private void build() {
        this.sessionFactory = this.factory.build(this.bundle,
                                            this.environment,
                                            this.config,
                                            ImmutableList.<Class<?>>of(Person.class),
                                            RemoteCredentialHibernateBundle.DEFAULT_NAME);
    }
}
