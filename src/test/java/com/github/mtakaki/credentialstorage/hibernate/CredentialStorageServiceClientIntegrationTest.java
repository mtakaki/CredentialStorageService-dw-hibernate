package com.github.mtakaki.credentialstorage.hibernate;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.validation.Valid;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.client.Client;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.hibernate.Criteria;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.criterion.Restrictions;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.mtakaki.credentialstorage.client.model.Credential;

import io.dropwizard.Application;
import io.dropwizard.Configuration;
import io.dropwizard.client.JerseyClientBuilder;
import io.dropwizard.client.JerseyClientConfiguration;
import io.dropwizard.db.DataSourceFactory;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;
import io.dropwizard.testing.ResourceHelpers;
import io.dropwizard.testing.junit.DropwizardAppRule;
import io.dropwizard.util.Duration;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class CredentialStorageServiceClientIntegrationTest {
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    @Table(name = "test_entity")
    @Entity
    @DynamicUpdate
    public static class TestEntity {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        @Column(name = "id", unique = true, nullable = false)
        @JsonIgnore
        private int id;

        @Column(name = "`key`", unique = true, nullable = false, length = 736)
        @JsonIgnore
        private String key;
    }

    public static class TestEntityDAO extends BundleAbstractDAO<TestEntity> {
        public TestEntityDAO(final RemoteCredentialHibernateBundle<?> bundle) {
            super(bundle);
        }

        public TestEntity get(final int id) {
            final Criteria criteria = this.criteria().add(Restrictions.eq("id", id));
            return this.uniqueResult(criteria);
        }

        public void save(final TestEntity entity) {
            this.persist(entity);
        }
    }

    @Path("/test")
    @AllArgsConstructor
    @Consumes
    @Produces(MediaType.APPLICATION_JSON)
    public static class TestResource {
        private final TestEntityDAO dao;

        @GET
        @Path("/{id}")
        @UnitOfWork
        public TestEntity get(@PathParam("id") final int id) {
            return this.dao.get(id);
        }

        @GET
        @Path("/wait")
        @UnitOfWork
        public TestEntity getLong() throws InterruptedException {
            Thread.sleep(5000L);
            return this.dao.get(28061);
        }

        @POST
        @UnitOfWork
        public Response post(@Valid final TestEntity entity) {
            this.dao.save(entity);
            return Response.created(URI.create(entity.getId() + "")).build();
        }
    }

    @Getter
    public static class SampleConfiguration extends Configuration {
        private final RemoteCredentialDataSourceFactory database = new RemoteCredentialDataSourceFactory();
    }

    public static class SampleApplication extends Application<SampleConfiguration> {
        private final RemoteCredentialHibernateBundle<SampleConfiguration> hibernate = new RemoteCredentialHibernateBundle<SampleConfiguration>(
                TestEntity.class) {
            @Override
            public DataSourceFactory getDataSourceFactory(
                    final SampleConfiguration configuration) {
                return configuration.getDatabase();
            }
        };

        @Override
        public void initialize(final Bootstrap<SampleConfiguration> bootstrap) {
            super.initialize(bootstrap);
            bootstrap.addBundle(this.hibernate);
        }

        @Override
        public void run(final SampleConfiguration configuration, final Environment environment)
                throws Exception {
            environment.jersey().register(new TestResource(new TestEntityDAO(this.hibernate)));
        }
    }

    private static final Credential CREDENTIAL = Credential.builder()
            .primary("my user")
            .secondary(
                    "a very long password that is actually not that long but we wanna test it anyway")
            .build();

    @Rule
    public final DropwizardAppRule<SampleConfiguration> RULE = new DropwizardAppRule<SampleConfiguration>(
            SampleApplication.class,
            ResourceHelpers.resourceFilePath("config.yml"));
    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    private Client client;

    @Before
    public void createClient() {
        final JerseyClientConfiguration configuration = new JerseyClientConfiguration();
        configuration.setTimeout(Duration.minutes(1L));
        configuration.setConnectionTimeout(Duration.minutes(1L));
        configuration.setConnectionRequestTimeout(Duration.minutes(1L));
        this.client = new JerseyClientBuilder(this.RULE.getEnvironment()).using(configuration)
                .build("test client");
    }

    @Test
    public void test() throws Exception {
        final MetricRegistry metric = new MetricRegistry();

        try (Timer.Context context = metric.timer("timer").time()) {
            final ExecutorService executorService = Executors.newFixedThreadPool(100);
            for (int i = 0; i < 50; i++) {
                executorService.submit(() -> {
                    assertThat(this.client
                            .target(String.format("http://localhost:%d/test/28061",
                                    this.RULE.getLocalPort()))
                            .request()
                            .get().getStatus()).isEqualTo(Status.OK.getStatusCode());
                });
            }
            for (int i = 0; i < 10; i++) {
                assertThat(this.client
                        .target(String.format("http://localhost:%d/test/wait",
                                this.RULE.getLocalPort()))
                        .request()
                        .get().getStatus()).isEqualTo(Status.OK.getStatusCode());
            }
            Thread.sleep(3000L);

            for (int i = 0; i < 60; i++) {
                executorService.submit(() -> {
                    assertThat(this.client
                            .target(String.format("http://localhost:%d/test/28061",
                                    this.RULE.getLocalPort()))
                            .request()
                            .get().getStatus()).isEqualTo(Status.OK.getStatusCode());
                });
            }

            assertThat(this.client
                    .target(String.format("http://localhost:%d/test/wait",
                            this.RULE.getLocalPort()))
                    .request()
                    .get().getStatus()).isEqualTo(Status.OK.getStatusCode());

            Thread.sleep(10000L);
            for (int i = 0; i < 20; i++) {
                executorService.submit(() -> {
                    assertThat(this.client
                            .target(String.format("http://localhost:%d/test/28061",
                                    this.RULE.getLocalPort()))
                            .request()
                            .get().getStatus()).isEqualTo(Status.OK.getStatusCode());
                });
            }
            executorService.shutdown();
            executorService.awaitTermination(5L, TimeUnit.SECONDS);
        }

        final ConsoleReporter reporter = ConsoleReporter.forRegistry(metric).build();
        reporter.report();
    }
}