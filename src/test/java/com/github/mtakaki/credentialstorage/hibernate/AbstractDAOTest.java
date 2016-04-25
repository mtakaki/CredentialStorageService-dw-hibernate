package com.github.mtakaki.credentialstorage.hibernate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.Serializable;
import java.util.List;

import org.hibernate.Criteria;
import org.hibernate.HibernateException;
import org.hibernate.Query;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.proxy.HibernateProxy;
import org.hibernate.proxy.LazyInitializer;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.google.common.collect.ImmutableList;

@RunWith(MockitoJUnitRunner.class)
public class AbstractDAOTest {
    private static class MockDAO extends BundleAbstractDAO<String> {
        public MockDAO(final RemoteCredentialHibernateBundle<?> bundle) {
            super(bundle);
        }

        @Override
        public Session currentSession() {
            return super.currentSession();
        }

        @Override
        public Criteria criteria() {
            return super.criteria();
        }

        @Override
        public Query namedQuery(final String queryName) throws HibernateException {
            return super.namedQuery(queryName);
        }

        @Override
        public Class<String> getEntityClass() {
            return super.getEntityClass();
        }

        @Override
        public String uniqueResult(final Criteria criteria) throws HibernateException {
            return super.uniqueResult(criteria);
        }

        @Override
        public String uniqueResult(final Query query) throws HibernateException {
            return super.uniqueResult(query);
        }

        @Override
        public List<String> list(final Criteria criteria) throws HibernateException {
            return super.list(criteria);
        }

        @Override
        public List<String> list(final Query query) throws HibernateException {
            return super.list(query);
        }

        @Override
        public String get(final Serializable id) {
            return super.get(id);
        }

        @Override
        public String persist(final String entity) throws HibernateException {
            return super.persist(entity);
        }

        @Override
        public <T> T initialize(final T proxy) {
            return super.initialize(proxy);
        }
    }

    @Mock
    private RemoteCredentialHibernateBundle<?> bundle;
    @Mock
    private SessionFactory factory;
    @Mock
    private Criteria criteria;
    @Mock
    private Query query;
    @Mock
    private Session session;

    private MockDAO dao;

    @Before
    public void setup() throws Exception {
        when(this.bundle.getSessionFactory()).thenReturn(this.factory);
        when(this.bundle.getCurrentThreadSessionFactory()).thenReturn(this.factory);
        when(this.factory.getCurrentSession()).thenReturn(this.session);
        when(this.session.createCriteria(String.class)).thenReturn(this.criteria);
        when(this.session.getNamedQuery(anyString())).thenReturn(this.query);

        this.dao = new MockDAO(this.bundle);
    }

    @Test
    public void getsASessionFromTheSessionFactory() throws Exception {
        assertThat(this.dao.currentSession())
                .isSameAs(this.session);
    }

    @Test
    public void hasAnEntityClass() throws Exception {
        assertThat(this.dao.getEntityClass())
                .isEqualTo(String.class);
    }

    @Test
    public void getsNamedQueries() throws Exception {
        assertThat(this.dao.namedQuery("query-name"))
                .isEqualTo(this.query);

        verify(this.session).getNamedQuery("query-name");
    }

    @Test
    public void createsNewCriteriaQueries() throws Exception {
        assertThat(this.dao.criteria())
                .isEqualTo(this.criteria);

        verify(this.session).createCriteria(String.class);
    }

    @Test
    public void returnsUniqueResultsFromCriteriaQueries() throws Exception {
        when(this.criteria.uniqueResult()).thenReturn("woo");

        assertThat(this.dao.uniqueResult(this.criteria))
                .isEqualTo("woo");
    }

    @Test
    public void returnsUniqueResultsFromQueries() throws Exception {
        when(this.query.uniqueResult()).thenReturn("woo");

        assertThat(this.dao.uniqueResult(this.query))
                .isEqualTo("woo");
    }

    @Test
    public void returnsUniqueListsFromCriteriaQueries() throws Exception {
        when(this.criteria.list()).thenReturn(ImmutableList.of("woo"));

        assertThat(this.dao.list(this.criteria))
                .containsOnly("woo");
    }


    @Test
    public void returnsUniqueListsFromQueries() throws Exception {
        when(this.query.list()).thenReturn(ImmutableList.of("woo"));

        assertThat(this.dao.list(this.query))
                .containsOnly("woo");
    }

    @Test
    public void getsEntitiesById() throws Exception {
        when(this.session.get(String.class, 200)).thenReturn("woo!");

        assertThat(this.dao.get(200))
                .isEqualTo("woo!");

        verify(this.session).get(String.class, 200);
    }

    @Test
    public void persistsEntities() throws Exception {
        assertThat(this.dao.persist("woo"))
                .isEqualTo("woo");

        verify(this.session).saveOrUpdate("woo");
    }

    @Test
    public void initializesProxies() throws Exception {
        final LazyInitializer initializer = mock(LazyInitializer.class);
        when(initializer.isUninitialized()).thenReturn(true);
        final HibernateProxy proxy = mock(HibernateProxy.class);
        when(proxy.getHibernateLazyInitializer()).thenReturn(initializer);

        this.dao.initialize(proxy);

        verify(initializer).initialize();
    }
}
