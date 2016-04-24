package com.github.mtakaki.credentialstorage.hibernate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.hibernate.HibernateException;
import org.hibernate.SQLQuery;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.junit.Test;
import org.mockito.InOrder;

import com.codahale.metrics.health.HealthCheck;

public class SessionFactoryHealthCheckTest {
    private final SessionFactory factory = mock(SessionFactory.class);
    private final SessionFactoryHealthCheck healthCheck = new SessionFactoryHealthCheck(this.factory,
            "SELECT 1");

    @Test
    public void hasASessionFactory() throws Exception {
        assertThat(this.healthCheck.getSessionFactory())
                .isEqualTo(this.factory);
    }

    @Test
    public void hasAValidationQuery() throws Exception {
        assertThat(this.healthCheck.getValidationQuery())
                .isEqualTo("SELECT 1");
    }

    @Test
    public void isHealthyIfNoExceptionIsThrown() throws Exception {
        final Session session = mock(Session.class);
        when(this.factory.openSession()).thenReturn(session);

        final Transaction transaction = mock(Transaction.class);
        when(session.beginTransaction()).thenReturn(transaction);

        final SQLQuery query = mock(SQLQuery.class);
        when(session.createSQLQuery(anyString())).thenReturn(query);

        assertThat(this.healthCheck.execute())
                .isEqualTo(HealthCheck.Result.healthy());

        final InOrder inOrder = inOrder(this.factory, session, transaction, query);
        inOrder.verify(this.factory).openSession();
        inOrder.verify(session).beginTransaction();
        inOrder.verify(session).createSQLQuery("SELECT 1");
        inOrder.verify(query).list();
        inOrder.verify(transaction).commit();
        inOrder.verify(session).close();
    }

    @Test
    public void isUnhealthyIfAnExceptionIsThrown() throws Exception {
        final Session session = mock(Session.class);
        when(this.factory.openSession()).thenReturn(session);

        final Transaction transaction = mock(Transaction.class);
        when(session.beginTransaction()).thenReturn(transaction);
        when(transaction.isActive()).thenReturn(true);

        final SQLQuery query = mock(SQLQuery.class);
        when(session.createSQLQuery(anyString())).thenReturn(query);
        when(query.list()).thenThrow(new HibernateException("OH NOE"));

        assertThat(this.healthCheck.execute().isHealthy())
                .isFalse();

        final InOrder inOrder = inOrder(this.factory, session, transaction, query);
        inOrder.verify(this.factory).openSession();
        inOrder.verify(session).beginTransaction();
        inOrder.verify(session).createSQLQuery("SELECT 1");
        inOrder.verify(query).list();
        inOrder.verify(transaction).rollback();
        inOrder.verify(session).close();

        verify(transaction, never()).commit();
    }
}
