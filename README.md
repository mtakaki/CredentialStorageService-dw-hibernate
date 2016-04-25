# Status
![Build Status](https://codeship.com/projects/72c79650-d302-0133-dc29-6af7e052eb76/status?branch=master)
[![Coverage Status](https://coveralls.io/repos/github/mtakaki/CredentialStorageService-dw-hibernate/badge.svg?branch=master)](https://coveralls.io/github/mtakaki/CredentialStorageService-dw-hibernate?branch=master)
[![Codacy Badge](https://api.codacy.com/project/badge/grade/ba1c873a2e5b46a39c7fdda5e9002990)](https://www.codacy.com/app/mitsuotakaki/CredentialStorageService-dw-hibernate)
[![Download](https://maven-badges.herokuapp.com/maven-central/com.github.mtakaki/dropwizard-credential-storage-hibernate/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.github.mtakaki/dropwizard-credential-storage-hibernate)
[![Javadoc](https://javadoc-emblem.rhcloud.com/doc/com.github.mtakaki/CredentialStorageService-dw-hibernate/badge.svg)](http://www.javadoc.io/doc/com.github.mtakaki/CredentialStorageService-dw-hibernate)

# CredentialStorageService-dw-hibernate
A [dropwizard](http://www.dropwizard.io) module that overrides the existing [hibernate package](https://dropwizard.github.io/dropwizard/0.9.2/docs/manual/hibernate.html) and adds the following features:

- Automatically retrieves database credentials from [credential server](https://github.com/mtakaki/CredentialStorageService).
    - Don't need to store the username and password in plain text in the configuration yaml file anymore.
- Credentials can be rotated and the clients are updated automatically **without downtime**.
    - The update frequency is configurable and connection is rotated automatically.
- Uses [dropwizard-hikaricp](https://github.com/mtakaki/dropwizard-hikaricp) instead of tomcat's connection pool.

Dropwizard **0.9.2** is the current version supported.

## Setup client

The client requires both the private and public RSA key in a DER format. The private key is never sent to the server, it's used to decrypt the symmetrical key used to encrypt the credentials.

### Converting private key

```
openssl pkcs8 -topk8 -inform PEM -outform DER -in id_rsa -nocrypt > private_key.der
```

### Converting public key

```
$ openssl rsa -in id_rsa -out public_key.der -outform DER -pubout
```

## Setup

### Bundle

```java
public class SampleApplication extends Application<SampleConfiguration> {
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
        bootstrap.addBundle(this.hibernate);
    }

    @Override
    public void run(final SampleConfiguration configuration, final Environment environment)
            throws Exception {
        environment.jersey().register(new TestResource(new TestEntityDAO(this.hibernate)));
    }
}
```

### BundleAbstractDAO

Overrides the `AbstractDAO` and it takes the `RemoteCredentialHibernateBundle`, rather than a `SessionFactory`. This is necessary to always retrieve an updated `SessionFactory`, which can be pointing to a new connection with the updated credentials. All the existing functionalities from dropwizard hibernate package will still work.

```java
public class TestEntityDAO extends BundleAbstractDAO<TestEntity> {
    public TestEntityDAO(final RemoteCredentialHibernateBundle<?> bundle) {
        super(bundle);
    }
    ...
}
```

### Configuration

```java
public class SampleConfiguration extends Configuration {
    private final RemoteCredentialDataSourceFactory database = new RemoteCredentialDataSourceFactory();
    
    public RemoteCredentialDataSourceFactory getDatabase() {
        return this.database;
    }
}
```

The configuration uses the same as dropwizard's hibernate package, adding the following properties:

```yaml
database:
  ...
  privateKeyFile: src/test/resources/private_key.der
  publicKeyFile: src/test/resources/public_key.der
  credentialServiceURL: https://credential-service.herokuapp.com/
  refreshFrequency: 7 # days
  credentialClientConfiguration:
    timeout: 1m
    connectionTimeout: 1m
  retrieveCredentials: true
```

#### `privateKeyFile` and `publicKeyFile`

The two RSA keys (private and public) that will be used for decrypting incoming data and sent to the credential service, respectively.

#### `credentialServiceURL`

This is the credential service endpoint. It should be reachable by the server, but ideally unreachable from the outside.

#### `refreshFrequency`

Beware the unit is in *days* for this setting, as it doesn't make sense refreshing for updated credentials more than once per day. This setting controls the frequency the credentials will be retrieved from the server. If there was any change in the credentials it will shutdown the existing connection (using the old credentials) and create a new one.

There's one caveat to this setting: this is relative to the server startup time. If you plan to have your server refreshing credentials every Monday (`refreshFrequency: 7`), but your server went down and rebooted on Thursday, your server will preserve the frequency and continue retrieving the credentials every 7 days after the server went up again.

#### `credentialClientConfiguration`

It follows jersey client configuration as described in [dropwizard client package](https://dropwizard.github.io/dropwizard/0.9.2/docs/manual/client.html).

#### `retrieveCredentials`

It's set to `true` by default and it controls the credential retrieval feature. When it's disabled it will behave the same way the current dropwizard package behaves.
