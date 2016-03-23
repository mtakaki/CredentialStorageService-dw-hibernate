package com.github.mtakaki.credentialstorage.hibernate;

import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.dropwizard.client.JerseyClientConfiguration;
import io.dropwizard.db.DataSourceFactory;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RemoteCredentialDataSourceFactory extends DataSourceFactory {
    @NotNull
    @JsonProperty
    private int refreshFrequency;
    @NotNull
    @JsonProperty
    private String credentialServiceURL;
    @JsonProperty
    private JerseyClientConfiguration credentialClientConfiguration = new JerseyClientConfiguration();
    @NotNull
    @JsonProperty
    private String privateKeyFile;
    @NotNull
    @JsonProperty
    private String publicKeyFile;
}