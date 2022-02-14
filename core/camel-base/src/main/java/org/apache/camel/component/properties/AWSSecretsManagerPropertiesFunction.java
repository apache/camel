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
package org.apache.camel.component.properties;

import java.util.Base64;

import org.apache.camel.spi.PropertiesFunction;
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
 * The credentials to access Secrets Manager is defined using three environment variables representing the static credentials:
 * <ul>
 * <li><tt>AWS_ACCESS_KEY</tt></li>
 * <li><tt>AWS_SECRET_KEY</tt></li>
 * <li><tt>AWS_REGION</tt></li>
 * </ul>
 * <p/>
 * This implementation is to return the secret value associated with a key.
 * The properties related to this kind of Properties Function are all prefixed with <tt>aws:</tt>.
 * For example asking for <tt>aws:token</tt>, will return the secret value associated the secret named token on AWS Secrets Manager
 */
public class AWSSecretsManagerPropertiesFunction implements PropertiesFunction {

    private static final String ACCESS_KEY = "AWS_ACCESS_KEY";
    private static final String SECRET_KEY = "AWS_SECRET_KEY";
    private static final String REGION = "AWS_REGION";

    @Override
    public String getName() {
        return "aws";
    }

    @Override
    public String apply(String remainder) {
        String key = remainder;
        String returnValue = null;

        SecretsManagerClient client = null;
        SecretsManagerClientBuilder clientBuilder = SecretsManagerClient.builder();

        if (remainder.contains(":")) {
            key = StringHelper.before(remainder, ":");
        }

        // make sure to use upper case
        if (key != null) {
            // a service should have both the host and port defined
            String accessKey = System.getenv(ACCESS_KEY);
            String secretKey = System.getenv(SECRET_KEY);
            String region = System.getenv(REGION);
            returnValue = getSecretFromSource(key, returnValue, clientBuilder, accessKey, secretKey, region);
        }

        return returnValue;
    }

    private String getSecretFromSource(
            String key, String returnValue, SecretsManagerClientBuilder clientBuilder, String accessKey, String secretKey,
            String region) {
        GetSecretValueRequest request;
        SecretsManagerClient client;
        if (accessKey != null && secretKey != null && region != null) {
            AwsBasicCredentials cred = AwsBasicCredentials.create(accessKey, secretKey);
            clientBuilder = clientBuilder.credentialsProvider(StaticCredentialsProvider.create(cred));
            clientBuilder.region(Region.of(region));
            client = clientBuilder.build();
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
        }
        return returnValue;
    }
}
