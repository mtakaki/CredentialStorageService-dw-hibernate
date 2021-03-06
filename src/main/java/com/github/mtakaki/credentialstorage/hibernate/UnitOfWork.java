package com.github.mtakaki.credentialstorage.hibernate;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import org.hibernate.CacheMode;
import org.hibernate.FlushMode;

/**
 * When annotating a Jersey resource method, wraps the method in a Hibernate
 * session.
 * <p>
 * To be used outside Jersey, one need to create a proxy of the component with
 * the annotated method.
 * </p>
 * .
 *
 * @see UnitOfWorkApplicationListener
 * @see UnitOfWorkAwareProxyFactory
 */
@Target(METHOD)
@Retention(RUNTIME)
@Documented
public @interface UnitOfWork {
    /**
     * If {@code true}, the Hibernate session will default to loading read-only
     * entities.
     *
     * @see org.hibernate.Session#setDefaultReadOnly(boolean)
     * @return {@code true} if the session is read-only and {@code false} if
     *         it's read-write.
     */
    boolean readOnly() default false;

    /**
     * If {@code true}, a transaction will be automatically started before the
     * resource method is invoked, committed if the method returned, and rolled
     * back if an exception was thrown.
     *
     * @return {@code true} if the transaction will be started automatically.
     */
    boolean transactional() default true;

    /**
     * The {@link CacheMode} for the session.
     *
     * @see CacheMode
     * @see org.hibernate.Session#setCacheMode(CacheMode)
     * @return The cache mode set for this unit of work.
     */
    CacheMode cacheMode() default CacheMode.NORMAL;

    /**
     * The {@link FlushMode} for the session.
     *
     * @see FlushMode
     * @see org.hibernate.Session#setFlushMode(org.hibernate.FlushMode)
     * @return The flush mode set for this unit of work.
     */
    FlushMode flushMode() default FlushMode.AUTO;

    /**
     * The name of a hibernate bundle (session factory) that specifies a
     * datasource against which a transaction will be opened.
     *
     * @return The name of the hibernate bundle.
     */
    String value() default RemoteCredentialHibernateBundle.DEFAULT_NAME;
}