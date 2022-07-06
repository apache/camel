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
package org.apache.camel.component.aws.secretsmanager;

import java.util.Base64;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.PropertiesFunction;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.apache.camel.vault.AwsVaultConfiguration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClientBuilder;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;
import software.amazon.awssdk.services.secretsmanager.model.SecretsManagerException;

/**
 * A {@link PropertiesFunction} that lookup the property value from AWS Secrets Manager service.
 * <p/>
 * The credentials to access Secrets Manager is defined using three environment variables representing the static
 * credentials:
 * <ul>
 * <li><tt>CAMEL_VAULT_AWS_ACCESS_KEY</tt></li>
 * <li><tt>CAMEL_VAULT_AWS_SECRET_KEY</tt></li>
 * <li><tt>CAMEL_VAULT_AWS_REGION</tt></li>
 * <li><tt>CAMEL_VAULT_AWS_USE_DEFAULT_CREDENTIALS_PROVIDER</tt></li>
 * </ul>
 * <p/>
 *
 * Otherwise it is possible to specify the credentials as properties:
 *
 * <ul>
 * <li><tt>camel.vault.aws.accessKey</tt></li>
 * <li><tt>camel.vault.aws.secretKey</tt></li>
 * <li><tt>camel.vault.aws.region</tt></li>
 * <li><tt>camel.vault.aws.useDefaultCredentialsProvider</tt></li>
 * </ul>
 * <p/>
 *
 * This implementation is to return the secret value associated with a key. The properties related to this kind of
 * Properties Function are all prefixed with <tt>aws:</tt>. For example asking for <tt>aws:token</tt>, will return the
 * secret value associated to the secret named token on AWS Secrets Manager.
 *
 * Another way of retrieving a secret value is using the following notation <tt>aws:database/username</tt>: in this case
 * the field username of the secret database will be returned. As a fallback, the user could provide a default value,
 * which will be returned in case the secret doesn't exist, the secret has been marked for deletion or, for example, if
 * a particular field of the secret doesn't exist. For using this feature, the user could use the following notation
 * <tt>aws:database/username:admin</tt>. The admin value will be returned as default value, if the conditions above were
 * all met.
 */

@org.apache.camel.spi.annotations.PropertiesFunction("aws")
public class SecretsManagerPropertiesFunction extends ServiceSupport implements PropertiesFunction, CamelContextAware {

    private static final String CAMEL_AWS_VAULT_ACCESS_KEY_ENV = "CAMEL_VAULT_AWS_ACCESS_KEY";
    private static final String CAMEL_AWS_VAULT_SECRET_KEY_ENV = "CAMEL_VAULT_AWS_SECRET_KEY";
    private static final String CAMEL_AWS_VAULT_REGION_ENV = "CAMEL_VAULT_AWS_REGION";
    private static final String CAMEL_AWS_VAULT_USE_DEFAULT_CREDENTIALS_PROVIDER_ENV
            = "CAMEL_VAULT_AWS_USE_DEFAULT_CREDENTIALS_PROVIDER";
    private CamelContext camelContext;
    private SecretsManagerClient client;

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        String accessKey = System.getenv(CAMEL_AWS_VAULT_ACCESS_KEY_ENV);
        String secretKey = System.getenv(CAMEL_AWS_VAULT_SECRET_KEY_ENV);
        String region = System.getenv(CAMEL_AWS_VAULT_REGION_ENV);
        boolean useDefaultCredentialsProvider
                = Boolean.parseBoolean(System.getenv(CAMEL_AWS_VAULT_USE_DEFAULT_CREDENTIALS_PROVIDER_ENV));
        if (ObjectHelper.isEmpty(accessKey) && ObjectHelper.isEmpty(secretKey) && ObjectHelper.isEmpty(region)) {
            AwsVaultConfiguration awsVaultConfiguration = getCamelContext().getVaultConfiguration().aws();
            if (ObjectHelper.isNotEmpty(awsVaultConfiguration)) {
                accessKey = awsVaultConfiguration.getAccessKey();
                secretKey = awsVaultConfiguration.getSecretKey();
                region = awsVaultConfiguration.getRegion();
                useDefaultCredentialsProvider = awsVaultConfiguration.isDefaultCredentialsProvider();
            }
        }
        if (ObjectHelper.isNotEmpty(accessKey) && ObjectHelper.isNotEmpty(secretKey) && ObjectHelper.isNotEmpty(region)) {
            SecretsManagerClientBuilder clientBuilder = SecretsManagerClient.builder();
            AwsBasicCredentials cred = AwsBasicCredentials.create(accessKey, secretKey);
            clientBuilder = clientBuilder.credentialsProvider(StaticCredentialsProvider.create(cred));
            clientBuilder.region(Region.of(region));
            client = clientBuilder.build();
        } else if (useDefaultCredentialsProvider && ObjectHelper.isNotEmpty(region)) {
            SecretsManagerClientBuilder clientBuilder = SecretsManagerClient.builder();
            clientBuilder.region(Region.of(region));
            client = clientBuilder.build();
        } else {
            throw new RuntimeCamelException(
                    "Using the AWS Secrets Manager Properties Function requires setting AWS credentials as application properties or environment variables");
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (client != null) {
            client.close();
        }
        super.doStop();
    }

    @Override
    public String getName() {
        return "aws";
    }

    @Override
    public String apply(String remainder) {
        String key = remainder;
        String subkey = null;
        String returnValue = null;
        String defaultValue = null;
        if (remainder.contains("/")) {
            key = StringHelper.before(remainder, "/");
            subkey = StringHelper.after(remainder, "/");
            defaultValue = StringHelper.after(subkey, ":");
            if (subkey.contains(":")) {
                subkey = StringHelper.before(subkey, ":");
            }
        } else if (remainder.contains(":")) {
            key = StringHelper.before(remainder, ":");
            defaultValue = StringHelper.after(remainder, ":");
        }

        if (key != null) {
            try {
                returnValue = getSecretFromSource(key, subkey, defaultValue);
            } catch (JsonProcessingException e) {
                throw new RuntimeCamelException("Something went wrong while recovering " + key + " from vault");
            }
        }

        return returnValue;
    }

    private String getSecretFromSource(
            String key, String subkey, String defaultValue)
            throws JsonProcessingException {
        String returnValue;
        GetSecretValueRequest request;
        GetSecretValueRequest.Builder builder = GetSecretValueRequest.builder();
        builder.secretId(key);
        request = builder.build();
        try {
            GetSecretValueResponse secret = client.getSecretValue(request);
            if (ObjectHelper.isNotEmpty(secret.secretString())) {
                returnValue = secret.secretString();
            } else {
                returnValue = new String(Base64.getDecoder().decode(secret.secretBinary().asByteBuffer()).array());
            }
            if (ObjectHelper.isNotEmpty(subkey)) {
                ObjectMapper mapper = new ObjectMapper();
                JsonNode actualObj = mapper.readTree(returnValue);
                JsonNode field = actualObj.get(subkey);
                if (ObjectHelper.isNotEmpty(field)) {
                    returnValue = field.textValue();
                } else {
                    returnValue = null;
                }
            }
            if (ObjectHelper.isEmpty(returnValue)) {
                returnValue = defaultValue;
            }
        } catch (SecretsManagerException ex) {
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
}
