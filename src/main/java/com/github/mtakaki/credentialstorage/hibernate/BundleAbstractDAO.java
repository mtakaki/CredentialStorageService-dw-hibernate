package com.github.mtakaki.credentialstorage.hibernate;

import org.hibernate.Session;
import org.hibernate.SessionFactory;

import io.dropwizard.hibernate.AbstractDAO;

/**
 * Extending the existing abstract base class for Hibernate DAO classes, but
 * instead of taking {@link SessionFactory} we take the
 * {@link RemoteCredentialHibernateBundle} to extract the session factory from.
 *
 * @param <E>
 *            the class which this DAO manages
 */
public class BundleAbstractDAO<E> extends AbstractDAO<E> {
    private final RemoteCredentialHibernateBundle<?> bundle;

    public BundleAbstractDAO(final RemoteCredentialHibernateBundle<?> bundle) {
        super(bundle.getDefaultSessionFactory());
        this.bundle = bundle;
    }

    @Override
    protected Session currentSession() {
        return this.bundle.getCurrentThreadSessionFactory().getCurrentSession();
    }
}