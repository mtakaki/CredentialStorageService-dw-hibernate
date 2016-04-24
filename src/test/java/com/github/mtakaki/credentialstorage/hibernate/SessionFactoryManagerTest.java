package com.github.mtakaki.credentialstorage.hibernate;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.hibernate.SessionFactory;
import org.junit.Test;

import io.dropwizard.db.ManagedDataSource;

public class SessionFactoryManagerTest {
    private final SessionFactory factory = mock(SessionFactory.class);
    private final ManagedDataSource dataSource = mock(ManagedDataSource.class);
    private final SessionFactoryManager manager = new SessionFactoryManager(this.factory, this.dataSource);

    @Test
    public void closesTheFactoryOnStopping() throws Exception {
        this.manager.stop();

        verify(this.factory).close();
    }

    @Test
    public void stopsTheDataSourceOnStopping() throws Exception {
        this.manager.stop();

        verify(this.dataSource).stop();
    }

    @Test
    public void startsTheDataSourceOnStarting() throws Exception {
        this.manager.start();

        verify(this.dataSource).start();
    }
}
