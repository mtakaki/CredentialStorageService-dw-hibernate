package com.github.mtakaki.credentialstorage.hibernate;

import java.util.Map;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.context.internal.ManagedSessionContext;

import com.github.mtakaki.credentialstorage.hibernate.util.SessionHolders;

import lombok.RequiredArgsConstructor;

/**
 * An aspect providing operations around a method with the {@link UnitOfWork} annotation.
 * It opens a Hibernate session and optionally a transaction.
 * <p>It should be created for every invocation of the method.</p>
 */
@RequiredArgsConstructor
public class UnitOfWorkAspect {
    private final Map<String, RemoteCredentialHibernateBundle<?>> bundles;

    // Context variables
    private UnitOfWork unitOfWork;
    private Session session;
    private SessionFactory sessionFactory;
    private RemoteCredentialHibernateBundle<?> bundle;
    private SessionHolders sessionHolders;

    public void beforeStart(final UnitOfWork unitOfWork) {
        if (unitOfWork == null) {
            return;
        }
        this.unitOfWork = unitOfWork;

        this.bundle = this.bundles.get(unitOfWork.value());
        if (this.bundle == null) {
            // If the user didn't specify the name of a session factory,
            // and we have only one registered, we can assume that it's the right one.
            if (unitOfWork.value().equals(RemoteCredentialHibernateBundle.DEFAULT_NAME) && this.bundles.size() == 1) {
                this.bundle = this.bundles.values().iterator().next();
            } else {
                throw new IllegalArgumentException("Unregistered Hibernate bundle: '" + unitOfWork.value() + "'");
            }
        }
        this.sessionHolders = this.bundle.getSessionHolders();
        this.sessionHolders.add(this);
        this.sessionFactory = this.bundle.getLatestSessionFactory();
        this.bundle.setLocalSessionFactory(this.sessionFactory);
        this.session = this.sessionFactory.openSession();
        try {
            this.configureSession();
            ManagedSessionContext.bind(this.session);
            this.beginTransaction();
        } catch (final Throwable th) {
            this.session.close();
            this.session = null;
            ManagedSessionContext.unbind(this.sessionFactory);
            this.sessionHolders.remove(this);
            throw th;
        }
    }

    public void afterEnd() {
        if (this.session == null) {
            return;
        }

        try {
            this.commitTransaction();
        } catch (final Exception e) {
            this.rollbackTransaction();
            throw e;
        } finally {
            this.session.close();
            this.session = null;
            ManagedSessionContext.unbind(this.sessionFactory);
            this.sessionHolders.remove(this);
        }

    }

    public void onError() {
        if (this.session == null) {
            return;
        }

        try {
            this.rollbackTransaction();
        } finally {
            this.session.close();
            this.session = null;
            ManagedSessionContext.unbind(this.sessionFactory);
            this.sessionHolders.remove(this);
        }
    }

    private void configureSession() {
        this.session.setDefaultReadOnly(this.unitOfWork.readOnly());
        this.session.setCacheMode(this.unitOfWork.cacheMode());
        this.session.setFlushMode(this.unitOfWork.flushMode());
    }

    private void beginTransaction() {
        if (!this.unitOfWork.transactional()) {
            return;
        }
        this.session.beginTransaction();
    }

    private void rollbackTransaction() {
        if (!this.unitOfWork.transactional()) {
            return;
        }
        final Transaction txn = this.session.getTransaction();
        if (txn != null && txn.isActive()) {
            txn.rollback();
        }
    }

    private void commitTransaction() {
        if (!this.unitOfWork.transactional()) {
            return;
        }
        final Transaction txn = this.session.getTransaction();
        if (txn != null && txn.isActive()) {
            txn.commit();
        }
    }
}