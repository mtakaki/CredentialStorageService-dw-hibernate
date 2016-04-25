package com.github.mtakaki.credentialstorage.hibernate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;

import org.glassfish.jersey.server.ExtendedUriInfo;
import org.glassfish.jersey.server.model.Resource;
import org.glassfish.jersey.server.model.ResourceMethod;
import org.glassfish.jersey.server.model.ResourceModel;
import org.glassfish.jersey.server.monitoring.ApplicationEvent;
import org.glassfish.jersey.server.monitoring.RequestEvent;
import org.glassfish.jersey.server.monitoring.RequestEventListener;
import org.hibernate.CacheMode;
import org.hibernate.FlushMode;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.context.internal.ManagedSessionContext;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

@RunWith(MockitoJUnitRunner.class)
public class UnitOfWorkApplicationListenerTest {
    private final SessionFactory sessionFactory = mock(SessionFactory.class);
    private final SessionFactory analyticsSessionFactory = mock(SessionFactory.class);
    @Mock
    private RemoteCredentialHibernateBundle<?> bundle;
    @Mock
    private RemoteCredentialHibernateBundle<?> analyticsBundle;

    private final UnitOfWorkApplicationListener listener = new UnitOfWorkApplicationListener();
    private final ApplicationEvent appEvent = mock(ApplicationEvent.class);
    private final ExtendedUriInfo uriInfo = mock(ExtendedUriInfo.class);

    private final RequestEvent requestStartEvent = mock(RequestEvent.class);
    private final RequestEvent requestMethodStartEvent = mock(RequestEvent.class);
    private final RequestEvent responseFiltersStartEvent = mock(RequestEvent.class);
    private final RequestEvent requestMethodExceptionEvent = mock(RequestEvent.class);
    private final Session session = mock(Session.class);
    private final Session analyticsSession = mock(Session.class);
    private final Transaction transaction = mock(Transaction.class);
    private final Transaction analyticsTransaction = mock(Transaction.class);

    @Before
    public void setUp() throws Exception {
        this.listener.registerBundle(RemoteCredentialHibernateBundle.DEFAULT_NAME, this.bundle);
        this.listener.registerBundle("analytics", this.analyticsBundle);

        final SessionHolders sessionHolders = mock(SessionHolders.class);

        when(this.bundle.getSessionHolders()).thenReturn(sessionHolders);
        when(this.analyticsBundle.getSessionHolders()).thenReturn(sessionHolders);
        when(this.bundle.getSessionFactory()).thenReturn(this.sessionFactory);
        when(this.analyticsBundle.getSessionFactory())
                .thenReturn(this.analyticsSessionFactory);

        when(this.sessionFactory.openSession()).thenReturn(this.session);
        when(this.session.getSessionFactory()).thenReturn(this.sessionFactory);
        when(this.session.beginTransaction()).thenReturn(this.transaction);
        when(this.session.getTransaction()).thenReturn(this.transaction);
        when(this.transaction.isActive()).thenReturn(true);

        when(this.analyticsSessionFactory.openSession()).thenReturn(this.analyticsSession);
        when(this.analyticsSession.getSessionFactory()).thenReturn(this.analyticsSessionFactory);
        when(this.analyticsSession.beginTransaction()).thenReturn(this.analyticsTransaction);
        when(this.analyticsSession.getTransaction()).thenReturn(this.analyticsTransaction);
        when(this.analyticsTransaction.isActive()).thenReturn(true);

        when(this.appEvent.getType()).thenReturn(ApplicationEvent.Type.INITIALIZATION_APP_FINISHED);
        when(this.requestMethodStartEvent.getType())
                .thenReturn(RequestEvent.Type.RESOURCE_METHOD_START);
        when(this.responseFiltersStartEvent.getType())
                .thenReturn(RequestEvent.Type.RESP_FILTERS_START);
        when(this.requestMethodExceptionEvent.getType()).thenReturn(RequestEvent.Type.ON_EXCEPTION);
        when(this.requestMethodStartEvent.getUriInfo()).thenReturn(this.uriInfo);
        when(this.responseFiltersStartEvent.getUriInfo()).thenReturn(this.uriInfo);
        when(this.requestMethodExceptionEvent.getUriInfo()).thenReturn(this.uriInfo);

        this.prepareAppEvent("methodWithDefaultAnnotation");
    }

    @Test
    public void opensAndClosesASession() throws Exception {
        this.execute();

        final InOrder inOrder = inOrder(this.sessionFactory, this.session);
        inOrder.verify(this.sessionFactory).openSession();
        inOrder.verify(this.session).close();
    }

    @Test
    public void bindsAndUnbindsTheSessionToTheManagedContext() throws Exception {
        doAnswer(new Answer<Object>() {
            @Override
            public Object answer(final InvocationOnMock invocation) throws Throwable {
                assertThat(ManagedSessionContext
                        .hasBind(UnitOfWorkApplicationListenerTest.this.sessionFactory))
                                .isTrue();
                return null;
            }
        }).when(this.session).beginTransaction();

        this.execute();

        assertThat(ManagedSessionContext.hasBind(this.sessionFactory)).isFalse();
    }

    @Test
    public void configuresTheSessionsReadOnlyDefault() throws Exception {
        this.prepareAppEvent("methodWithReadOnlyAnnotation");

        this.execute();

        verify(this.session).setDefaultReadOnly(true);
    }

    @Test
    public void configuresTheSessionsCacheMode() throws Exception {
        this.prepareAppEvent("methodWithCacheModeIgnoreAnnotation");

        this.execute();

        verify(this.session).setCacheMode(CacheMode.IGNORE);
    }

    @Test
    public void configuresTheSessionsFlushMode() throws Exception {
        this.prepareAppEvent("methodWithFlushModeAlwaysAnnotation");

        this.execute();

        verify(this.session).setFlushMode(FlushMode.ALWAYS);
    }

    @Test
    public void doesNotBeginATransactionIfNotTransactional() throws Exception {
        final String resourceMethodName = "methodWithTransactionalFalseAnnotation";
        this.prepareAppEvent(resourceMethodName);

        when(this.session.getTransaction()).thenReturn(null);

        this.execute();

        verify(this.session, never()).beginTransaction();
        verifyZeroInteractions(this.transaction);
    }

    @Test
    public void detectsAnnotationOnHandlingMethod() throws NoSuchMethodException {
        final String resourceMethodName = "handlingMethodAnnotated";
        this.prepareAppEvent(resourceMethodName);

        this.execute();

        verify(this.session).setDefaultReadOnly(true);
    }

    @Test
    public void detectsAnnotationOnDefinitionMethod() throws NoSuchMethodException {
        final String resourceMethodName = "definitionMethodAnnotated";
        this.prepareAppEvent(resourceMethodName);

        this.execute();

        verify(this.session).setDefaultReadOnly(true);
    }

    @Test
    public void annotationOnDefinitionMethodOverridesHandlingMethod() throws NoSuchMethodException {
        final String resourceMethodName = "bothMethodsAnnotated";
        this.prepareAppEvent(resourceMethodName);

        this.execute();

        verify(this.session).setDefaultReadOnly(true);
    }

    @Test
    public void beginsAndCommitsATransactionIfTransactional() throws Exception {
        this.execute();

        final InOrder inOrder = inOrder(this.session, this.transaction);
        inOrder.verify(this.session).beginTransaction();
        inOrder.verify(this.transaction).commit();
        inOrder.verify(this.session).close();
    }

    @Test
    public void rollsBackTheTransactionOnException() throws Exception {
        this.executeWithException();

        final InOrder inOrder = inOrder(this.session, this.transaction);
        inOrder.verify(this.session).beginTransaction();
        inOrder.verify(this.transaction).rollback();
        inOrder.verify(this.session).close();
    }

    @Test
    public void doesNotCommitAnInactiveTransaction() throws Exception {
        when(this.transaction.isActive()).thenReturn(false);

        this.execute();

        verify(this.transaction, never()).commit();
    }

    @Test
    public void doesNotCommitANullTransaction() throws Exception {
        when(this.session.getTransaction()).thenReturn(null);

        this.execute();

        verify(this.transaction, never()).commit();
    }

    @Test
    public void doesNotRollbackAnInactiveTransaction() throws Exception {
        when(this.transaction.isActive()).thenReturn(false);

        this.executeWithException();

        verify(this.transaction, never()).rollback();
    }

    @Test
    public void doesNotRollbackANullTransaction() throws Exception {
        when(this.session.getTransaction()).thenReturn(null);

        this.executeWithException();

        verify(this.transaction, never()).rollback();
    }

    @Test
    public void beginsAndCommitsATransactionForAnalytics() throws Exception {
        when(this.sessionFactory.openSession()).thenReturn(this.session);

        this.prepareAppEvent("methodWithUnitOfWorkOnAnalyticsDatabase");
        this.execute();

        final InOrder inOrder = inOrder(this.analyticsSession, this.analyticsTransaction);
        inOrder.verify(this.analyticsSession).beginTransaction();
        inOrder.verify(this.analyticsTransaction).commit();
        inOrder.verify(this.analyticsSession).close();
    }

    @Test
    public void throwsExceptionOnNotRegisteredDatabase() throws Exception {
        try {
            this.prepareAppEvent("methodWithUnitOfWorkOnNotRegisteredDatabase");
            this.execute();
            Assert.fail();
        } catch (final IllegalArgumentException e) {
            Assert.assertEquals(e.getMessage(), "Unregistered Hibernate bundle: 'warehouse'");
        }
    }

    private void prepareAppEvent(final String resourceMethodName) throws NoSuchMethodException {
        final Resource.Builder builder = Resource.builder();
        final MockResource mockResource = new MockResource();
        final Method handlingMethod = mockResource.getClass().getMethod(resourceMethodName);

        Method definitionMethod = handlingMethod;
        final Class<?> interfaceClass = mockResource.getClass().getInterfaces()[0];
        if (this.methodDefinedOnInterface(resourceMethodName, interfaceClass.getMethods())) {
            definitionMethod = interfaceClass.getMethod(resourceMethodName);
        }

        final ResourceMethod resourceMethod = builder.addMethod()
                .handlingMethod(handlingMethod)
                .handledBy(mockResource, definitionMethod).build();
        final Resource resource = builder.build();
        final ResourceModel model = new ResourceModel.Builder(false).addResource(resource).build();

        when(this.appEvent.getResourceModel()).thenReturn(model);
        when(this.uriInfo.getMatchedResourceMethod()).thenReturn(resourceMethod);
    }

    private boolean methodDefinedOnInterface(final String methodName, final Method[] methods) {
        for (final Method method : methods) {
            if (method.getName().equals(methodName)) {
                return true;
            }
        }
        return false;
    }

    private void execute() {
        this.listener.onEvent(this.appEvent);
        final RequestEventListener requestListener = this.listener
                .onRequest(this.requestStartEvent);
        requestListener.onEvent(this.requestMethodStartEvent);
        requestListener.onEvent(this.responseFiltersStartEvent);
    }

    private void executeWithException() {
        this.listener.onEvent(this.appEvent);
        final RequestEventListener requestListener = this.listener
                .onRequest(this.requestStartEvent);
        requestListener.onEvent(this.requestMethodStartEvent);
        requestListener.onEvent(this.requestMethodExceptionEvent);
    }

    public static class MockResource implements MockResourceInterface {

        @UnitOfWork(
            readOnly = false,
            cacheMode = CacheMode.NORMAL,
            transactional = true,
            flushMode = FlushMode.AUTO)
        public void methodWithDefaultAnnotation() {
        }

        @UnitOfWork(
            readOnly = true,
            cacheMode = CacheMode.NORMAL,
            transactional = true,
            flushMode = FlushMode.AUTO)
        public void methodWithReadOnlyAnnotation() {
        }

        @UnitOfWork(
            readOnly = false,
            cacheMode = CacheMode.IGNORE,
            transactional = true,
            flushMode = FlushMode.AUTO)
        public void methodWithCacheModeIgnoreAnnotation() {
        }

        @UnitOfWork(
            readOnly = false,
            cacheMode = CacheMode.NORMAL,
            transactional = true,
            flushMode = FlushMode.ALWAYS)
        public void methodWithFlushModeAlwaysAnnotation() {
        }

        @UnitOfWork(
            readOnly = false,
            cacheMode = CacheMode.NORMAL,
            transactional = false,
            flushMode = FlushMode.AUTO)
        public void methodWithTransactionalFalseAnnotation() {
        }

        @UnitOfWork(readOnly = true)
        @Override
        public void handlingMethodAnnotated() {
        }

        @Override
        public void definitionMethodAnnotated() {
        }

        @UnitOfWork(readOnly = false)
        @Override
        public void bothMethodsAnnotated() {

        }

        @UnitOfWork("analytics")
        public void methodWithUnitOfWorkOnAnalyticsDatabase() {

        }

        @UnitOfWork("warehouse")
        public void methodWithUnitOfWorkOnNotRegisteredDatabase() {

        }
    }

    public static interface MockResourceInterface {

        void handlingMethodAnnotated();

        @UnitOfWork(readOnly = true)
        void definitionMethodAnnotated();

        @UnitOfWork(readOnly = true)
        void bothMethodsAnnotated();
    }
}
