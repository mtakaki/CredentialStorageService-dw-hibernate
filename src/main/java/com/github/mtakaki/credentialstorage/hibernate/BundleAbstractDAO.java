package com.github.mtakaki.credentialstorage.hibernate;

import org.hibernate.Session;

import io.dropwizard.hibernate.AbstractDAO;

public class BundleAbstractDAO<E> extends AbstractDAO<E> {
    private final RemoteCredentialHibernateBundle<?> bundle;

    public BundleAbstractDAO(final RemoteCredentialHibernateBundle<?> bundle) {
        super(bundle.getSessionFactory());
        this.bundle = bundle;
    }

    @Override
    protected Session currentSession() {
        return this.bundle.getUpdatedSessionFactory().getCurrentSession();
    }
}