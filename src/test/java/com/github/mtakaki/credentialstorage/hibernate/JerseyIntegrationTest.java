package com.github.mtakaki.credentialstorage.hibernate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.failBecauseExceptionWasNotThrown;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.glassfish.jersey.client.ClientConfig;
import org.glassfish.jersey.test.JerseyTest;
import org.glassfish.jersey.test.TestProperties;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.junit.After;
import org.junit.Test;

import com.codahale.metrics.MetricRegistry;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;

import io.dropwizard.db.DataSourceFactory;
import io.dropwizard.jackson.Jackson;
import io.dropwizard.jersey.DropwizardResourceConfig;
import io.dropwizard.jersey.errors.ErrorMessage;
import io.dropwizard.jersey.jackson.JacksonMessageBodyProvider;
import io.dropwizard.jersey.validation.Validators;
import io.dropwizard.lifecycle.setup.LifecycleEnvironment;
import io.dropwizard.logging.BootstrapLogging;
import io.dropwizard.setup.Environment;

public class JerseyIntegrationTest extends JerseyTest {
    static {
        BootstrapLogging.bootstrap();
    }

    public static class PersonDAO extends BundleAbstractDAO<Person> {
        public PersonDAO(final RemoteCredentialHibernateBundle<?> bundle) {
            super(bundle);
        }

        public Optional<Person> findByName(final String name) {
            return Optional.fromNullable(this.get(name));
        }

        @Override
        public Person persist(final Person entity) {
            return super.persist(entity);
        }
    }

    @Path("/people/{name}")
    @Produces(MediaType.APPLICATION_JSON)
    public static class PersonResource {
        private final PersonDAO dao;

        public PersonResource(final PersonDAO dao) {
            this.dao = dao;
        }

        @GET
        @UnitOfWork(readOnly = true)
        public Optional<Person> find(@PathParam("name") final String name) {
            return this.dao.findByName(name);
        }

        @PUT
        @UnitOfWork
        public void save(final Person person) {
            this.dao.persist(person);
        }
    }

    private SessionFactory sessionFactory;
    private RemoteCredentialHibernateBundle<?> bundle;

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();

        if (this.sessionFactory != null) {
            this.sessionFactory.close();
        }
    }

    @Override
    protected Application configure() {
        this.forceSet(TestProperties.CONTAINER_PORT, "0");

        final MetricRegistry metricRegistry = new MetricRegistry();
        final SessionFactoryFactory factory = new SessionFactoryFactory();
        final DataSourceFactory dbConfig = new DataSourceFactory();
        this.bundle = mock(RemoteCredentialHibernateBundle.class);

        final SessionHolders sessionHolders = mock(SessionHolders.class);
        when(this.bundle.getSessionHolders()).thenReturn(sessionHolders);

        final Environment environment = mock(Environment.class);
        final LifecycleEnvironment lifecycleEnvironment = mock(LifecycleEnvironment.class);
        when(environment.lifecycle()).thenReturn(lifecycleEnvironment);
        when(environment.metrics()).thenReturn(metricRegistry);

        dbConfig.setUrl("jdbc:hsqldb:mem:DbTest-" + System.nanoTime()
                + "?hsqldb.translate_dti_types=false");
        dbConfig.setUser("sa");
        dbConfig.setDriverClass("org.hsqldb.jdbcDriver");
        dbConfig.setValidationQuery("SELECT 1 FROM INFORMATION_SCHEMA.SYSTEM_USERS");

        this.sessionFactory = factory.build(this.bundle,
                environment,
                dbConfig,
                ImmutableList.<Class<?>> of(Person.class),
                RemoteCredentialHibernateBundle.DEFAULT_NAME);
        when(this.bundle.getSessionFactory()).thenReturn(this.sessionFactory);
        when(this.bundle.getCurrentThreadSessionFactory()).thenReturn(this.sessionFactory);

        final Session session = this.sessionFactory.openSession();
        try {
            session.createSQLQuery("DROP TABLE people IF EXISTS").executeUpdate();
            session.createSQLQuery(
                    "CREATE TABLE people (name varchar(100) primary key, email varchar(16), birthday timestamp with time zone)")
                    .executeUpdate();
            session.createSQLQuery(
                    "INSERT INTO people VALUES ('Coda', 'coda@example.com', '1979-01-02 00:22:00+0:00')")
                    .executeUpdate();
        } finally {
            session.close();
        }

        final DropwizardResourceConfig config = DropwizardResourceConfig
                .forTesting(new MetricRegistry());
        config.register(new UnitOfWorkApplicationListener("hr-db", this.bundle));
        config.register(new PersonResource(new PersonDAO(this.bundle)));
        config.register(new JacksonMessageBodyProvider(Jackson.newObjectMapper(),
                Validators.newValidator()));
        config.register(new DataExceptionMapper());

        return config;
    }

    @Override
    protected void configureClient(final ClientConfig config) {
        config.register(new JacksonMessageBodyProvider(Jackson.newObjectMapper(),
                Validators.newValidator()));
    }

    @Test
    public void findsExistingData() throws Exception {
        final Person coda = this.target("/people/Coda").request(MediaType.APPLICATION_JSON)
                .get(Person.class);

        assertThat(coda.getName())
                .isEqualTo("Coda");

        assertThat(coda.getEmail())
                .isEqualTo("coda@example.com");

        assertThat(coda.getBirthday())
                .isEqualTo(new DateTime(1979, 1, 2, 0, 22, DateTimeZone.UTC));
    }

    @Test
    public void doesNotFindMissingData() throws Exception {
        try {
            this.target("/people/Poof").request(MediaType.APPLICATION_JSON)
                    .get(Person.class);
            failBecauseExceptionWasNotThrown(WebApplicationException.class);
        } catch (final WebApplicationException e) {
            assertThat(e.getResponse().getStatus())
                    .isEqualTo(404);
        }
    }

    @Test
    public void createsNewData() throws Exception {
        final Person person = new Person();
        person.setName("Hank");
        person.setEmail("hank@example.com");
        person.setBirthday(new DateTime(1971, 3, 14, 19, 12, DateTimeZone.UTC));

        this.target("/people/Hank").request()
                .put(Entity.entity(person, MediaType.APPLICATION_JSON));

        final Person hank = this.target("/people/Hank")
                .request(MediaType.APPLICATION_JSON)
                .get(Person.class);

        assertThat(hank.getName())
                .isEqualTo("Hank");

        assertThat(hank.getEmail())
                .isEqualTo("hank@example.com");

        assertThat(hank.getBirthday())
                .isEqualTo(person.getBirthday());
    }

    @Test
    public void testSqlExceptionIsHandled() throws Exception {
        final Person person = new Person();
        person.setName("Jeff");
        person.setEmail("jeff.hammersmith@targetprocessinc.com");
        person.setBirthday(new DateTime(1984, 2, 11, 0, 0, DateTimeZone.UTC));

        final Response response = this.target("/people/Jeff").request()
                .put(Entity.entity(person, MediaType.APPLICATION_JSON));

        assertThat(response.getStatusInfo()).isEqualTo(Response.Status.BAD_REQUEST);
        assertThat(response.getHeaderString(HttpHeaders.CONTENT_TYPE))
                .isEqualTo(MediaType.APPLICATION_JSON);
        assertThat(response.readEntity(ErrorMessage.class).getMessage()).isEqualTo("Wrong email");
    }
}
