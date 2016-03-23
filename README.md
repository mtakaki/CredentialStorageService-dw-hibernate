# Status
![Build Status](https://codeship.com/projects/72c79650-d302-0133-dc29-6af7e052eb76/status?branch=master)
[![Coverage Status](https://coveralls.io/repos/github/mtakaki/CredentialStorageService-dw-hibernate/badge.svg?branch=master)](https://coveralls.io/github/mtakaki/CredentialStorageService-dw-hibernate?branch=master)
[![Download](https://maven-badges.herokuapp.com/maven-central/com.github.mtakaki/dropwizard-credential-storage-hibernate/badge.svg)](https://maven-badges.herokuapp.com/maven-central/com.github.mtakaki/dropwizard-credential-storage-hibernate)
[![Javadoc](https://javadoc-emblem.rhcloud.com/doc/com.github.mtakaki/CredentialStorageService-dw-hibernate/badge.svg)](http://www.javadoc.io/doc/com.github.mtakaki/CredentialStorageService-dw-hibernate)

# CredentialStorageService-dw-hibernate
A dropwizard module that overrides the existing [hibernate package](https://dropwizard.github.io/dropwizard/0.9.2/docs/manual/hibernate.html) and adds the following features:

- Automatically retrieves database credentials from [credential server](https://github.com/mtakaki/CredentialStorageService).
- Credentials can be rotated and the clients are updated automatically.
    - The settings update frequency is configurable.

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

## Configuration

The configuration uses the same as dropwizard's hibernate package, adding the following properties:

```yaml
database:
  ...
  privateKeyFile: src/test/resources/private_key.der
  publicKeyFile: src/test/resources/public_key.der
  credentialServiceURL: https://credential-service.herokuapp.com/
  refreshFrequency: 7
  credentialClientConfiguration:
    timeout: 1m
    connectionTimeout: 1m
```

### `credentialServiceURL`

This is the credential service endpoint. It should be reachable by the server, but ideally unreachable from the outside.

### `refreshFrequency`

This setting controls the frequency the credentials will be retrieved from the server. If there was any change in the credentials it will shutdown the existing connection (using the old credentials) and create a new one.

There's one caveat to this setting: this is relative to the server startup time. If you plan to have your server refreshing credentials every Monday (`refreshFrequency: 7`), but your server went down and rebooted on Thursday, your server will preserve the frequency and continue retrieving the credentials every 7 days after the server went up again.

If it's set to `0` and `user` and `password` settings are set, it will deactivate the remote credential functionality and it won't refresh the credentials. It will behave the same way the current dropwizard package behaves.

### `credentialClientConfiguration`

It follows jersey client configuration as described in [dropwizard client package](https://dropwizard.github.io/dropwizard/0.9.2/docs/manual/client.html).