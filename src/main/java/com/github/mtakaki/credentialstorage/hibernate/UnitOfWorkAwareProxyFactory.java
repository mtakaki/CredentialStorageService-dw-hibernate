package com.github.mtakaki.credentialstorage.hibernate;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import com.google.common.collect.ImmutableMap;

import javassist.util.proxy.MethodHandler;
import javassist.util.proxy.Proxy;
import javassist.util.proxy.ProxyFactory;

/**
 * A factory for creating proxies for components that use Hibernate data access objects
 * outside Jersey resources.
 * <p>A created proxy will be aware of the {@link UnitOfWork} annotation
 * on the original class methods and will open a Hibernate session with a transaction
 * around them.</p>
 */
public class UnitOfWorkAwareProxyFactory {

    private final ImmutableMap<String, RemoteCredentialHibernateBundle<?>> sessionFactories;

    public UnitOfWorkAwareProxyFactory(final String name, final RemoteCredentialHibernateBundle<?> sessionFactory) {
        this.sessionFactories = ImmutableMap.of(name, sessionFactory);
    }

    public UnitOfWorkAwareProxyFactory(final RemoteCredentialHibernateBundle<?>... bundles) {
        final ImmutableMap.Builder<String, RemoteCredentialHibernateBundle<?>> sessionFactoriesBuilder = ImmutableMap.builder();
        for (final RemoteCredentialHibernateBundle<?> bundle : bundles) {
            sessionFactoriesBuilder.put(bundle.name(), bundle);
        }
        this.sessionFactories = sessionFactoriesBuilder.build();
    }


    /**
     * Creates a new <b>@UnitOfWork</b> aware proxy of a class with the default constructor.
     *
     * @param clazz the specified class definition
     * @param <T>   the type of the class
     * @return a new proxy
     */
    public <T> T create(final Class<T> clazz) {
        return this.create(clazz, new Class<?>[]{}, new Object[]{});
    }

    /**
     * Creates a new <b>@UnitOfWork</b> aware proxy of a class with an one-parameter constructor.
     *
     * @param clazz                the specified class definition
     * @param constructorParamType the type of the constructor parameter
     * @param constructorArguments the argument passed to the constructor
     * @param <T>                  the type of the class
     * @return a new proxy
     */
    public <T> T create(final Class<T> clazz, final Class<?> constructorParamType, final Object constructorArguments) {
        return this.create(clazz, new Class<?>[]{constructorParamType}, new Object[]{constructorArguments});
    }

    /**
     * Creates a new <b>@UnitOfWork</b> aware proxy of a class with a complex constructor.
     *
     * @param clazz                 the specified class definition
     * @param constructorParamTypes the types of the constructor parameters
     * @param constructorArguments  the arguments passed to the constructor
     * @param <T>                   the type of the class
     * @return a new proxy
     */
    @SuppressWarnings("unchecked")
    public <T> T create(final Class<T> clazz, final Class<?>[] constructorParamTypes, final Object[] constructorArguments) {
        final ProxyFactory factory = new ProxyFactory();
        factory.setSuperclass(clazz);

        try {
            final Proxy proxy = (Proxy) (constructorParamTypes.length == 0 ?
                    factory.createClass().newInstance() :
                    factory.create(constructorParamTypes, constructorArguments));
            proxy.setHandler(new MethodHandler() {
                @Override
                public Object invoke(final Object self, final Method overridden, final Method proceed, final Object[] args) throws Throwable {
                    final UnitOfWork unitOfWork = overridden.getAnnotation(UnitOfWork.class);
                    final UnitOfWorkAspect unitOfWorkAspect = new UnitOfWorkAspect(UnitOfWorkAwareProxyFactory.this.sessionFactories);
                    try {
                        unitOfWorkAspect.beforeStart(unitOfWork);
                        final Object result = proceed.invoke(self, args);
                        unitOfWorkAspect.afterEnd();
                        return result;
                    } catch (final InvocationTargetException e) {
                        unitOfWorkAspect.onError();
                        throw e.getCause();
                    } catch (final Exception e) {
                        unitOfWorkAspect.onError();
                        throw e;
                    }
                }
            });
            return (T) proxy;
        } catch (NoSuchMethodException | InstantiationException | IllegalAccessException |
                InvocationTargetException e) {
            throw new IllegalStateException("Unable to create a proxy for the class '" + clazz + "'", e);
        }
    }
}