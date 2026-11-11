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
package org.apache.camel.component.hashicorp.vault;

import java.net.URI;
import java.util.function.Consumer;

import org.apache.camel.util.ObjectHelper;
import org.springframework.vault.authentication.AwsIamAuthentication;
import org.springframework.vault.authentication.AwsIamAuthenticationOptions;
import org.springframework.vault.authentication.ClientAuthentication;
import org.springframework.vault.client.VaultClient;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;

/**
 * The aws-iam login. It is in a class of its own, as it needs the AWS SDK (software.amazon.awssdk:auth), an optional
 * dependency of the component: the class is loaded only for the aws-iam login.
 */
final class HashicorpVaultAwsIam {

    private HashicorpVaultAwsIam() {
    }

    /**
     * The aws-iam login, which signs its request with the credentials of the AWS SDK default chain: environment
     * variables, system properties, web identity token (such as IAM roles for service accounts on EKS), profile,
     * container (ECS) or instance profile (EC2).
     *
     * @param closeOnStop takes the credentials provider, to close with the client
     */
    static ClientAuthentication authentication(
            HashicorpVaultClientSettings settings, VaultClient loginClient, Consumer<AutoCloseable> closeOnStop) {
        DefaultCredentialsProvider credentials = DefaultCredentialsProvider.builder().build();
        closeOnStop.accept(credentials);
        return new AwsIamAuthentication(options(settings, credentials), loginClient);
    }

    static AwsIamAuthenticationOptions options(HashicorpVaultClientSettings settings, AwsCredentialsProvider credentials) {
        AwsIamAuthenticationOptions.AwsIamAuthenticationOptionsBuilder builder = AwsIamAuthenticationOptions.builder()
                .credentialsProvider(credentials)
                .role(settings.getRole());
        if (ObjectHelper.isNotEmpty(settings.getAwsRegion())) {
            builder.region(Region.of(settings.getAwsRegion()));
        }
        if (ObjectHelper.isNotEmpty(settings.getAwsStsEndpoint())) {
            builder.endpointUri(URI.create(settings.getAwsStsEndpoint()));
        }
        if (ObjectHelper.isNotEmpty(settings.getAwsIamServerId())) {
            builder.serverId(settings.getAwsIamServerId());
        }
        if (ObjectHelper.isNotEmpty(settings.getAuthMountPath())) {
            builder.path(settings.getAuthMountPath());
        }
        return builder.build();
    }
}
