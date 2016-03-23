package com.github.mtakaki.credentialstorage.hibernate;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;

import com.codahale.metrics.health.HealthCheck;
import com.google.common.util.concurrent.MoreExecutors;

import io.dropwizard.db.TimeBoundHealthCheck;
import io.dropwizard.util.Duration;

public class SessionFactoryHealthCheck extends HealthCheck {
    private final SessionFactory sessionFactory;
    private final String validationQuery;
    private final TimeBoundHealthCheck timeBoundHealthCheck;

    public SessionFactoryHealthCheck(final SessionFactory sessionFactory,
                                     final String validationQuery) {
        this(MoreExecutors.newDirectExecutorService(), Duration.seconds(0), sessionFactory, validationQuery);
    }

    public SessionFactoryHealthCheck(final ExecutorService executorService,
                                     final Duration duration,
                                     final SessionFactory sessionFactory,
                                     final String validationQuery) {
        this.sessionFactory = sessionFactory;
        this.validationQuery = validationQuery;
        this.timeBoundHealthCheck = new TimeBoundHealthCheck(executorService, duration);
    }


    public SessionFactory getSessionFactory() {
        return this.sessionFactory;
    }

    public String getValidationQuery() {
        return this.validationQuery;
    }

    @Override
    protected Result check() throws Exception {
        return this.timeBoundHealthCheck.check(new Callable<Result>() {
            @Override
            public Result call() throws Exception {
                final Session session = SessionFactoryHealthCheck.this.sessionFactory.openSession();
                try {
                    final Transaction txn = session.beginTransaction();
                    try {
                        session.createSQLQuery(SessionFactoryHealthCheck.this.validationQuery).list();
                        txn.commit();
                    } catch (final Exception e) {
                        if (txn.isActive()) {
                            txn.rollback();
                        }
                        throw e;
                    }
                } finally {
                    session.close();
                }
                return Result.healthy();
            }
        });
    }
}