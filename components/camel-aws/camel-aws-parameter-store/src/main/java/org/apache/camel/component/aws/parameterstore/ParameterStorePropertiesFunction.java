/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.aws.parameterstore;

import java.net.URI;
import java.util.HashSet;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.PropertiesFunction;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.vault.AwsVaultConfiguration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ssm.SsmClient;
import software.amazon.awssdk.services.ssm.SsmClientBuilder;
import software.amazon.awssdk.services.ssm.model.GetParameterRequest;
import software.amazon.awssdk.services.ssm.model.GetParameterResponse;
import software.amazon.awssdk.services.ssm.model.ParameterNotFoundException;

/**
 * A {@link PropertiesFunction} that lookup the property value from AWS Systems Manager Parameter Store.
 * <p/>
 * The credentials to access Parameter Store is defined using three environment variables representing the static
 * credentials:
 * <ul>
 * <li><tt>CAMEL_VAULT_AWS_ACCESS_KEY</tt></li>
 * <li><tt>CAMEL_VAULT_AWS_SECRET_KEY</tt></li>
 * <li><tt>CAMEL_VAULT_AWS_REGION</tt></li>
 * <li><tt>CAMEL_VAULT_AWS_USE_DEFAULT_CREDENTIALS_PROVIDER</tt></li>
 * <li><tt>CAMEL_VAULT_AWS_USE_PROFILE_CREDENTIALS_PROVIDER</tt></li>
 * <li><tt>CAMEL_AWS_VAULT_PROFILE_NAME</tt></li>
 * </ul>
 * <p/>
 *
 * Otherwise, it is possible to specify the credentials as properties:
 *
 * <ul>
 * <li><tt>camel.vault.aws.accessKey</tt></li>
 * <li><tt>camel.vault.aws.secretKey</tt></li>
 * <li><tt>camel.vault.aws.region</tt></li>
 * <li><tt>camel.vault.aws.defaultCredentialsProvider</tt></li>
 * <li><tt>camel.vault.aws.profileCredentialsProvider</tt></li>
 * <li><tt>camel.vault.aws.profileName</tt></li>
 * </ul>
 * <p/>
 *
 * This implementation is to return the parameter value associated with a key. The properties related to this kind of
 * Properties Function are all prefixed with <tt>aws-parameterstore:</tt>. For example asking for
 * <tt>aws-parameterstore:/my/param</tt>, will return the parameter value associated with the parameter named /my/param
 * on AWS Parameter Store.
 *
 * The user could provide a default value, which will be returned in case the parameter doesn't exist. For using this
 * feature, the user could use the following notation <tt>aws-parameterstore:/my/param:defaultValue</tt>. The
 * defaultValue will be returned if the parameter doesn't exist.
 *
 * For SecureString parameters, the value will be automatically decrypted.
 */
@org.apache.camel.spi.annotations.PropertiesFunction("aws-parameterstore")
public class ParameterStorePropertiesFunction extends ServiceSupport implements PropertiesFunction, CamelContextAware {

    private static final String CAMEL_AWS_VAULT_ACCESS_KEY_ENV = "CAMEL_VAULT_AWS_ACCESS_KEY";
    private static final String CAMEL_AWS_VAULT_SECRET_KEY_ENV = "CAMEL_VAULT_AWS_SECRET_KEY";
    private static final String CAMEL_AWS_VAULT_REGION_ENV = "CAMEL_VAULT_AWS_REGION";
    private static final String CAMEL_AWS_VAULT_USE_DEFAULT_CREDENTIALS_PROVIDER_ENV
            = "CAMEL_VAULT_AWS_USE_DEFAULT_CREDENTIALS_PROVIDER";

    private static final String CAMEL_AWS_VAULT_USE_PROFILE_CREDENTIALS_PROVIDER_ENV
            = "CAMEL_VAULT_AWS_USE_PROFILE_CREDENTIALS_PROVIDER";

    private static final String CAMEL_AWS_VAULT_PROFILE_NAME_ENV
            = "CAMEL_AWS_VAULT_PROFILE_NAME";

    private static final String CAMEL_AWS_VAULT_IS_OVERRIDE_ENDPOINT
            = "CAMEL_AWS_VAULT_IS_OVERRIDE_ENDPOINT";

    private static final String CAMEL_AWS_VAULT_URI_ENDPOINT_OVERRIDE = "CAMEL_AWS_VAULT_URI_ENDPOINT_OVERRIDE";

    private CamelContext camelContext;
    private SsmClient client;

    private final Set<String> parameters = new HashSet<>();

    private String region;
    private boolean defaultCredentialsProvider;

    private boolean profleCredentialsProvider;

    private String profileName;

    private boolean isOverrideEndpoint;

    private String uriEndpointOverride;

    public ParameterStorePropertiesFunction() {
        super();
    }

    public ParameterStorePropertiesFunction(SsmClient client) {
        super();
        this.client = client;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        String accessKey = System.getenv(CAMEL_AWS_VAULT_ACCESS_KEY_ENV);
        String secretKey = System.getenv(CAMEL_AWS_VAULT_SECRET_KEY_ENV);
        String region = System.getenv(CAMEL_AWS_VAULT_REGION_ENV);
        boolean useDefaultCredentialsProvider
                = Boolean.parseBoolean(System.getenv(CAMEL_AWS_VAULT_USE_DEFAULT_CREDENTIALS_PROVIDER_ENV));
        boolean useProfileCredentialsProvider
                = Boolean.parseBoolean(System.getenv(CAMEL_AWS_VAULT_USE_PROFILE_CREDENTIALS_PROVIDER_ENV));
        String profileName = System.getenv(CAMEL_AWS_VAULT_PROFILE_NAME_ENV);
        boolean isOverrideEndpoint = Boolean.parseBoolean(System.getenv(CAMEL_AWS_VAULT_IS_OVERRIDE_ENDPOINT));
        String uriEndpointOverride = System.getenv(CAMEL_AWS_VAULT_URI_ENDPOINT_OVERRIDE);
        if (ObjectHelper.isEmpty(accessKey) && ObjectHelper.isEmpty(secretKey) && ObjectHelper.isEmpty(region)) {
            AwsVaultConfiguration awsVaultConfiguration = getCamelContext().getVaultConfiguration().aws();
            if (ObjectHelper.isNotEmpty(awsVaultConfiguration)) {
                accessKey = awsVaultConfiguration.getAccessKey();
                secretKey = awsVaultConfiguration.getSecretKey();
                region = awsVaultConfiguration.getRegion();
                useDefaultCredentialsProvider = awsVaultConfiguration.isDefaultCredentialsProvider();
                useProfileCredentialsProvider = awsVaultConfiguration.isProfileCredentialsProvider();
                profileName = awsVaultConfiguration.getProfileName();
                isOverrideEndpoint = awsVaultConfiguration.isOverrideEndpoint();
                uriEndpointOverride = awsVaultConfiguration.getUriEndpointOverride();
            }
        }
        this.region = region;
        if (ObjectHelper.isNotEmpty(accessKey) && ObjectHelper.isNotEmpty(secretKey) && ObjectHelper.isNotEmpty(region)) {
            SsmClientBuilder clientBuilder = SsmClient.builder();
            AwsBasicCredentials cred = AwsBasicCredentials.create(accessKey, secretKey);
            clientBuilder = clientBuilder.credentialsProvider(StaticCredentialsProvider.create(cred));
            clientBuilder.region(Region.of(region));
            if (isOverrideEndpoint) {
                if (ObjectHelper.isNotEmpty(uriEndpointOverride)) {
                    clientBuilder.endpointOverride(URI.create(uriEndpointOverride));
                }
            }
            client = clientBuilder.build();
        } else if (useDefaultCredentialsProvider && ObjectHelper.isNotEmpty(region)) {
            this.defaultCredentialsProvider = true;
            SsmClientBuilder clientBuilder = SsmClient.builder();
            clientBuilder.region(Region.of(region));
            if (isOverrideEndpoint) {
                if (ObjectHelper.isNotEmpty(uriEndpointOverride)) {
                    clientBuilder.endpointOverride(URI.create(uriEndpointOverride));
                }
            }
            client = clientBuilder.build();
        } else if (useProfileCredentialsProvider && ObjectHelper.isNotEmpty(profileName)) {
            this.profleCredentialsProvider = true;
            this.profileName = profileName;
            SsmClientBuilder clientBuilder = SsmClient.builder();
            clientBuilder.credentialsProvider(ProfileCredentialsProvider.create(profileName));
            clientBuilder.region(Region.of(region));
            if (isOverrideEndpoint) {
                if (ObjectHelper.isNotEmpty(uriEndpointOverride)) {
                    clientBuilder.endpointOverride(URI.create(uriEndpointOverride));
                }
            }
            client = clientBuilder.build();
        } else {
            throw new RuntimeCamelException(
                    "Using the AWS Parameter Store Properties Function requires setting AWS credentials as application properties or environment variables");
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (client != null) {
            try {
                client.close();
            } catch (Exception e) {
                // ignore
            }
            client = null;
        }
        parameters.clear();
        super.doStop();
    }

    @Override
    public String getName() {
        return "aws-parameterstore";
    }

    @Override
    public String apply(String remainder) {
        String key = remainder;
        String returnValue = null;
        String defaultValue = null;

        if (remainder.contains(":")) {
            key = StringHelper.before(remainder, ":");
            defaultValue = StringHelper.after(remainder, ":");
        }

        if (key != null) {
            try {
                returnValue = getParameterFromSource(key, defaultValue);
            } catch (Exception e) {
                throw new RuntimeCamelException(
                        "Error getting parameter from Parameter Store using key: " + key + " due to: " + e.getMessage(), e);
            }
        }

        return returnValue;
    }

    private String getParameterFromSource(String key, String defaultValue) {

        // capture name of parameter
        parameters.add(key);

        String returnValue;
        GetParameterRequest request;
        GetParameterRequest.Builder builder = GetParameterRequest.builder();
        builder.name(key);
        // Always decrypt SecureString parameters
        builder.withDecryption(true);
        request = builder.build();
        try {
            GetParameterResponse parameter = client.getParameter(request);
            returnValue = parameter.parameter().value();
            if (ObjectHelper.isEmpty(returnValue)) {
                returnValue = defaultValue;
            }
        } catch (ParameterNotFoundException ex) {
            if (ObjectHelper.isNotEmpty(defaultValue)) {
                returnValue = defaultValue;
            } else {
                throw ex;
            }
        }
        return returnValue;
    }

    @Override
    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    @Override
    public CamelContext getCamelContext() {
        return camelContext;
    }

    /**
     * Names of the parameters in use
     */
    public Set<String> getParameters() {
        return parameters;
    }

    /**
     * The region in use for connecting to AWS Parameter Store
     */
    public String getRegion() {
        return region;
    }

    /**
     * Whether login is using default credentials provider
     */
    public boolean isDefaultCredentialsProvider() {
        return defaultCredentialsProvider;
    }

    /**
     * Whether login is using default profile credentials provider
     */
    public boolean isProfleCredentialsProvider() {
        return profleCredentialsProvider;
    }

    /**
     * The profile name to use when using the profile credentials provider
     */
    public String getProfileName() {
        return profileName;
    }
}
