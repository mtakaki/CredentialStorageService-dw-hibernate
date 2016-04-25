package com.github.mtakaki.credentialstorage.hibernate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.hibernate.SessionFactory;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import io.dropwizard.db.ManagedDataSource;

@RunWith(MockitoJUnitRunner.class)
public class SessionHoldersTest {
    private SessionHolders holders;

    @Mock
    private SessionFactory sessionFactory;

    @Mock
    private ManagedDataSource dataSource;

    @Before
    public void setup() {
        this.holders = new SessionHolders(this.sessionFactory, this.dataSource);
    }

    @Test
    public void testAdd() {
        final UnitOfWorkAspect unitOfWork = mock(UnitOfWorkAspect.class);
        this.holders.add(unitOfWork);
    }

    @Test
    public void testCloseConnections() throws Exception {
        this.holders.closeConnections();

        verify(this.sessionFactory, times(1)).close();
        verify(this.dataSource, times(1)).stop();
    }

    @Test
    public void testRemoveWithLastUnitOfWorkAndSetToClose() throws Exception {
        final UnitOfWorkAspect unitOfWork = mock(UnitOfWorkAspect.class);
        this.holders.setCloseSession(true);

        this.holders.remove(unitOfWork);

        verify(this.sessionFactory, times(1)).close();
        verify(this.dataSource, times(1)).stop();
    }

    @Test
    public void testRemoveWithLastUnitOfWorkAndNotSetToClose() throws Exception {
        final UnitOfWorkAspect unitOfWork = mock(UnitOfWorkAspect.class);
        this.holders.setCloseSession(false);

        this.holders.remove(unitOfWork);

        verify(this.sessionFactory, never()).close();
        verify(this.dataSource, never()).stop();
    }

    @Test
    public void testRemoveWithMoreThanOneUnitOfWorkAndSetToClose() throws Exception {
        final UnitOfWorkAspect unitOfWork = mock(UnitOfWorkAspect.class);
        this.holders.setCloseSession(true);
        this.holders.add(unitOfWork);
        this.holders.add(mock(UnitOfWorkAspect.class));

        this.holders.remove(unitOfWork);

        verify(this.sessionFactory, never()).close();
        verify(this.dataSource, never()).stop();
    }

    @Test
    public void testRemoveWithMoreThanOneUnitOfWorkAndNotSetToClose() throws Exception {
        final UnitOfWorkAspect unitOfWork = mock(UnitOfWorkAspect.class);
        this.holders.setCloseSession(false);
        this.holders.add(unitOfWork);
        this.holders.add(mock(UnitOfWorkAspect.class));

        this.holders.remove(unitOfWork);

        verify(this.sessionFactory, never()).close();
        verify(this.dataSource, never()).stop();
    }

    @Test
    public void testIsEmptyWhenItsNotEmpty() {
        this.holders.add(mock(UnitOfWorkAspect.class));

        assertThat(this.holders.isEmpty()).isFalse();
    }

    @Test
    public void testIsEmptyWhenItsEmpty() {
        assertThat(this.holders.isEmpty()).isTrue();
    }

    @Test
    public void testCloseConnectionsWithException() throws Exception {
        final UnitOfWorkAspect unitOfWork = mock(UnitOfWorkAspect.class);
        this.holders.setCloseSession(true);
        doThrow(Exception.class).when(this.dataSource).stop();

        this.holders.remove(unitOfWork);

        verify(this.sessionFactory, times(1)).close();
        verify(this.dataSource, times(1)).stop();
    }
}