package com.github.mtakaki.credentialstorage.hibernate;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import javax.ws.rs.ext.Provider;

import org.glassfish.jersey.server.internal.process.MappableException;
import org.glassfish.jersey.server.model.Resource;
import org.glassfish.jersey.server.model.ResourceMethod;
import org.glassfish.jersey.server.monitoring.ApplicationEvent;
import org.glassfish.jersey.server.monitoring.ApplicationEventListener;
import org.glassfish.jersey.server.monitoring.RequestEvent;
import org.glassfish.jersey.server.monitoring.RequestEventListener;
import org.hibernate.SessionFactory;

/**
 * An application event listener that listens for Jersey application initialization to
 * be finished, then creates a map of resource method that have metrics annotations.
 *
 * Finally, it listens for method start events, and returns a {@link RequestEventListener}
 * that updates the relevant metric for suitably annotated methods when it gets the
 * request events indicating that the method is about to be invoked, or just got done
 * being invoked.
 */
@Provider
public class UnitOfWorkApplicationListener implements ApplicationEventListener {
    private final Map<Method, UnitOfWork> methodMap = new HashMap<>();
    private final Map<String, RemoteCredentialHibernateBundle<?>> bundles = new HashMap<>();

    public UnitOfWorkApplicationListener() {
    }

    /**
     * Construct an application event listener using the given name and session factory.
     *
     * <p/>
     * When using this constructor, the {@link UnitOfWorkApplicationListener}
     * should be added to a Jersey {@code ResourceConfig} as a singleton.
     *
     * @param name a name of a Hibernate bundle
     * @param bundle a {@link SessionFactory}
     */
    public UnitOfWorkApplicationListener(final String name, final RemoteCredentialHibernateBundle<?> bundle) {
        this.registerBundle(name, bundle);
    }

    /**
     * Register a session factory with the given name.
     *
     * @param name a name of a Hibernate bundle
     * @param bundle a {@link SessionFactory}
     */
    public void registerBundle(final String name, final RemoteCredentialHibernateBundle<?> bundle) {
        this.bundles.put(name, bundle);
    }

    private static class UnitOfWorkEventListener implements RequestEventListener {
        private final Map<Method, UnitOfWork> methodMap;
        private final UnitOfWorkAspect unitOfWorkAspect;

        public UnitOfWorkEventListener(final Map<Method, UnitOfWork> methodMap,
                                       final Map<String, RemoteCredentialHibernateBundle<?>> sessionFactories) {
            this.methodMap = methodMap;
            this.unitOfWorkAspect = new UnitOfWorkAspect(sessionFactories);
        }

        @Override
        public void onEvent(final RequestEvent event) {
            if (event.getType() == RequestEvent.Type.RESOURCE_METHOD_START) {
                final UnitOfWork unitOfWork = this.methodMap.get(event.getUriInfo()
                        .getMatchedResourceMethod().getInvocable().getDefinitionMethod());
                this.unitOfWorkAspect.beforeStart(unitOfWork);
            } else if (event.getType() == RequestEvent.Type.RESP_FILTERS_START) {
                try {
                    this.unitOfWorkAspect.afterEnd();
                } catch (final Exception e) {
                    throw new MappableException(e);
                }
            } else if (event.getType() == RequestEvent.Type.ON_EXCEPTION) {
                this.unitOfWorkAspect.onError();
            }
        }
    }

    @Override
    public void onEvent(final ApplicationEvent event) {
        if (event.getType() == ApplicationEvent.Type.INITIALIZATION_APP_FINISHED) {
            for (final Resource resource : event.getResourceModel().getResources()) {
                for (final ResourceMethod method : resource.getAllMethods()) {
                    this.registerUnitOfWorkAnnotations(method);
                }

                for (final Resource childResource : resource.getChildResources()) {
                    for (final ResourceMethod method : childResource.getAllMethods()) {
                        this.registerUnitOfWorkAnnotations(method);
                    }
                }
            }
        }
    }

    @Override
    public RequestEventListener onRequest(final RequestEvent event) {
        return new UnitOfWorkEventListener(this.methodMap, this.bundles);
    }

    private void registerUnitOfWorkAnnotations(final ResourceMethod method) {
        UnitOfWork annotation = method.getInvocable().getDefinitionMethod().getAnnotation(UnitOfWork.class);

        if (annotation == null) {
            annotation = method.getInvocable().getHandlingMethod().getAnnotation(UnitOfWork.class);
        }

        if (annotation != null) {
            this.methodMap.put(method.getInvocable().getDefinitionMethod(), annotation);
        }

    }
}
