package com.github.mtakaki.credentialstorage.hibernate;

import org.hibernate.SessionFactory;

import com.google.common.annotations.VisibleForTesting;

import io.dropwizard.db.ManagedDataSource;
import io.dropwizard.lifecycle.Managed;

public class SessionFactoryManager implements Managed {
    private final SessionFactory factory;
    private final ManagedDataSource dataSource;

    public SessionFactoryManager(final SessionFactory factory, final ManagedDataSource dataSource) {
        this.factory = factory;
        this.dataSource = dataSource;
    }

    @VisibleForTesting
    ManagedDataSource getDataSource() {
        return this.dataSource;
    }

    @Override
    public void start() throws Exception {
        this.dataSource.start();
    }

    @Override
    public void stop() throws Exception {
        this.factory.close();
        this.dataSource.stop();
    }
}