package com.github.mtakaki.credentialstorage.hibernate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.codahale.metrics.MetricRegistry;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

import io.dropwizard.db.DataSourceFactory;
import io.dropwizard.lifecycle.setup.LifecycleEnvironment;
import io.dropwizard.logging.BootstrapLogging;
import io.dropwizard.setup.Environment;

@RunWith(MockitoJUnitRunner.class)
public class UnitOfWorkAwareProxyFactoryTest {
    static {
        BootstrapLogging.bootstrap();
    }

    private SessionFactory sessionFactory;

    @Mock
    private RemoteCredentialHibernateBundle<?> bundle;

    @Mock
    private SessionHolders sessionHolders;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    public void setUp() throws Exception {
        final Environment environment = mock(Environment.class);
        when(environment.lifecycle()).thenReturn(mock(LifecycleEnvironment.class));
        when(environment.metrics()).thenReturn(new MetricRegistry());

        when(this.bundle.getSessionHolders()).thenReturn(this.sessionHolders);

        final DataSourceFactory dataSourceFactory = new DataSourceFactory();
        dataSourceFactory.setUrl("jdbc:hsqldb:mem:unit-of-work-" + UUID.randomUUID().toString());
        dataSourceFactory.setUser("sa");
        dataSourceFactory.setDriverClass("org.hsqldb.jdbcDriver");
        dataSourceFactory.setValidationQuery("SELECT 1 FROM INFORMATION_SCHEMA.SYSTEM_USERS");
        dataSourceFactory.setProperties(ImmutableMap.of("hibernate.dialect", "org.hibernate.dialect.HSQLDialect"));
//        dataSourceFactory.setInitialSize(1);
        dataSourceFactory.setMinSize(1);

        this.sessionFactory = new SessionFactoryFactory()
                .build(this.bundle, environment, dataSourceFactory, ImmutableList.<Class<?>>of(), RemoteCredentialHibernateBundle.DEFAULT_NAME);
        when(this.bundle.getCurrentThreadSessionFactory()).thenReturn(this.sessionFactory);
        final Session session = this.sessionFactory.openSession();
        try {
            session.createSQLQuery("create table user_sessions (token varchar(64) primary key, username varchar(16))")
                    .executeUpdate();
            session.createSQLQuery("insert into user_sessions values ('67ab89d', 'jeff_28')")
                    .executeUpdate();
        } finally {
            session.close();
        }
    }

    @Test
    public void testProxyWorks() throws Exception {
        final SessionDao sessionDao = new SessionDao(this.sessionFactory);
        final UnitOfWorkAwareProxyFactory unitOfWorkAwareProxyFactory =
                new UnitOfWorkAwareProxyFactory("default", this.bundle);

        final OAuthAuthenticator oAuthAuthenticator = unitOfWorkAwareProxyFactory
                .create(OAuthAuthenticator.class, SessionDao.class, sessionDao);
        assertThat(oAuthAuthenticator.authenticate("67ab89d")).isTrue();
        assertThat(oAuthAuthenticator.authenticate("bd1e23a")).isFalse();
    }

    @Test
    public void testProxyWorksWithoutUnitOfWork() {
        assertThat(new UnitOfWorkAwareProxyFactory("default", this.bundle)
                .create(PlainAuthenticator.class)
                .authenticate("c82d11e"))
                .isTrue();
    }

    @Test
    public void testProxyHandlesErrors() {
        this.thrown.expect(IllegalStateException.class);
        this.thrown.expectMessage("Session cluster is down");

        new UnitOfWorkAwareProxyFactory("default", this.bundle)
                .create(BrokenAuthenticator.class)
                .authenticate("b812ae4");
    }

    static class SessionDao {

        private final SessionFactory sessionFactory;

        public SessionDao(final SessionFactory sessionFactory) {
            this.sessionFactory = sessionFactory;
        }

        public boolean isExist(final String token) {
            return this.sessionFactory.getCurrentSession()
                    .createSQLQuery("select username from user_sessions where token=:token")
                    .setParameter("token", token)
                    .list()
                    .size() > 0;
        }

    }

    static class OAuthAuthenticator {

        private final SessionDao sessionDao;

        public OAuthAuthenticator(final SessionDao sessionDao) {
            this.sessionDao = sessionDao;
        }

        @UnitOfWork
        public boolean authenticate(final String token) {
            return this.sessionDao.isExist(token);
        }
    }

    static class PlainAuthenticator {

        public boolean authenticate(final String token) {
            return true;
        }
    }

    static class BrokenAuthenticator {

        @UnitOfWork
        public boolean authenticate(final String token) {
            throw new IllegalStateException("Session cluster is down");
        }
    }
}