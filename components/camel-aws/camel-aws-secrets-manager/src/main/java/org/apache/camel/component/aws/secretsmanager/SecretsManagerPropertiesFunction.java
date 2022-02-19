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
import java.util.Optional;
import java.util.Properties;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.PropertiesComponent;
import org.apache.camel.spi.PropertiesFunction;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClientBuilder;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueRequest;
import software.amazon.awssdk.services.secretsmanager.model.GetSecretValueResponse;

/**
 * A {@link PropertiesFunction} that lookup the property value from AWS Secrets Manager service.
 * <p/>
 * The credentials to access Secrets Manager is defined using three environment variables representing the static
 * credentials:
 * <ul>
 * <li><tt>CAMEL_VAULT_AWS_ACCESS_KEY</tt></li>
 * <li><tt>CAMEL_VAULT_AWS_SECRET_KEY</tt></li>
 * <li><tt>CAMEL_VAULT_AWS_REGION</tt></li>
 * </ul>
 * <p/>
 * This implementation is to return the secret value associated with a key. The properties related to this kind of
 * Properties Function are all prefixed with <tt>aws:</tt>. For example asking for <tt>aws:token</tt>, will return the
 * secret value associated the secret named token on AWS Secrets Manager
 */

@org.apache.camel.spi.annotations.PropertiesFunction("aws")
public class SecretsManagerPropertiesFunction extends ServiceSupport implements PropertiesFunction, CamelContextAware {

    private static final String CAMEL_AWS_VAULT_ACCESS_KEY_ENV = "CAMEL_VAULT_AWS_ACCESS_KEY";
    private static final String CAMEL_AWS_VAULT_SECRET_KEY_ENV = "CAMEL_VAULT_AWS_SECRET_KEY";
    private static final String CAMEL_AWS_VAULT_REGION_ENV = "CAMEL_VAULT_AWS_REGION";
    private static final String CAMEL_AWS_VAULT_ACCESS_KEY_PROP = "camel.aws.vault.access.key";
    private static final String CAMEL_AWS_VAULT_SECRET_KEY_PROP = "camel.aws.vault.secret.key";
    private static final String CAMEL_AWS_VAULT_REGION_PROP = "camel.aws.vault.region";
    private CamelContext camelContext;
    private SecretsManagerClient client;

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        String accessKey = System.getenv(CAMEL_AWS_VAULT_ACCESS_KEY_ENV);
        String secretKey = System.getenv(CAMEL_AWS_VAULT_SECRET_KEY_ENV);
        String region = System.getenv(CAMEL_AWS_VAULT_REGION_ENV);
        if (ObjectHelper.isEmpty(accessKey) && ObjectHelper.isEmpty(secretKey) && ObjectHelper.isEmpty(region)) {
            PropertiesComponent pc = getCamelContext().getPropertiesComponent();
            Optional<String> tmpAccessKey = pc.resolveProperty(CAMEL_AWS_VAULT_ACCESS_KEY_PROP);
            if (tmpAccessKey.isPresent()) {
                accessKey = tmpAccessKey.get();
            }
            Optional<String> tmpSecretKey = pc.resolveProperty(CAMEL_AWS_VAULT_SECRET_KEY_PROP);
            if (tmpSecretKey.isPresent()) {
                secretKey = tmpSecretKey.get();
            }
            Optional<String> tmpRegion = pc.resolveProperty(CAMEL_AWS_VAULT_REGION_PROP);
            if (tmpRegion.isPresent()) {
                region = tmpRegion.get();
            }
        }
        SecretsManagerClientBuilder clientBuilder = SecretsManagerClient.builder();
        AwsBasicCredentials cred = AwsBasicCredentials.create(accessKey, secretKey);
        clientBuilder = clientBuilder.credentialsProvider(StaticCredentialsProvider.create(cred));
        clientBuilder.region(Region.of(region));
        client = clientBuilder.build();
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

        if (remainder.contains(":")) {
            key = StringHelper.before(remainder, ":");
            subkey = StringHelper.after(remainder, ":");
        }

        if (key != null) {
            try {
                returnValue = getSecretFromSource(key, subkey);
            } catch (JsonProcessingException e) {
                throw new RuntimeCamelException("Something went wrong while recovering " + key + " from vault");
            }
        }

        return returnValue;
    }

    private String getSecretFromSource(
            String key, String subkey)
            throws JsonProcessingException {
        String returnValue;
        GetSecretValueRequest request;
        GetSecretValueRequest.Builder builder = GetSecretValueRequest.builder();
        builder.secretId(key);
        request = builder.build();
        GetSecretValueResponse secret = client.getSecretValue(request);
        returnValue = secret.secretString();
        if (secret.secretString() != null) {
            returnValue = secret.secretString();
        } else {
            returnValue = new String(Base64.getDecoder().decode(secret.secretBinary().asByteBuffer()).array());
        }
        if (subkey != null) {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode actualObj = mapper.readTree(returnValue);
            JsonNode field = actualObj.get(subkey);
            if (ObjectHelper.isNotEmpty(field)) {
                returnValue = field.textValue();
            } else {
                returnValue = null;
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
