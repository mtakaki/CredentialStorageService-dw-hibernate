package com.github.mtakaki.credentialstorage.hibernate;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.dropwizard.client.JerseyClientConfiguration;
import io.dropwizard.db.DataSourceFactory;
import io.dropwizard.validation.ValidationMethod;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class RemoteCredentialDataSourceFactory extends DataSourceFactory {
    @JsonProperty
    @Min(1)
    @Max(365)
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
    @JsonProperty
    private boolean retrieveCredentials = true;

    @JsonIgnore
    @ValidationMethod(
        message = ".refreshFrequency must be less greater than zero when credential retrieval is enabled")
    public boolean isRefreshFrequencySetWhenFeatureIsEnabled() {
        return this.retrieveCredentials ? this.refreshFrequency > 1 : true;
    }
}