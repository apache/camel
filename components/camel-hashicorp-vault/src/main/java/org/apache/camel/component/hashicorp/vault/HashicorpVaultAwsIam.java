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
import software.amazon.awssdk.core.SdkSystemSetting;
import software.amazon.awssdk.regions.Region;

/**
 * The aws-iam login. It is in a class of its own, as it needs the AWS SDK (software.amazon.awssdk:auth), an optional
 * dependency of the component: the class is loaded only for the aws-iam login.
 */
final class HashicorpVaultAwsIam {

    // the class of the sts module of the AWS SDK that the web identity provider of the default chain loads
    private static final String STS_WEB_IDENTITY_FACTORY
            = "software.amazon.awssdk.services.sts.internal.StsWebIdentityCredentialsProviderFactory";

    private HashicorpVaultAwsIam() {
    }

    /**
     * Checks that the AWS SDK can use the web identity token of the application, when it has one: without the sts
     * module, the default chain skips the web identity token and goes on to other credentials, such as those of the
     * instance, so the application would log in to Vault as another identity than its own.
     *
     * @throws IllegalArgumentException when a web identity token is set and the sts module is missing
     */
    static void validate() {
        // read as the web identity provider of the AWS SDK reads it: system property, then environment variable
        if (SdkSystemSetting.AWS_WEB_IDENTITY_TOKEN_FILE.getStringValue().isPresent()
                && ObjectHelper.loadClass(STS_WEB_IDENTITY_FACTORY, HashicorpVaultAwsIam.class.getClassLoader()) == null) {
            throw new IllegalArgumentException(
                    "The aws-iam login with a web identity token (AWS_WEB_IDENTITY_TOKEN_FILE) needs the sts module of the"
                                               + " AWS SDK: add the software.amazon.awssdk:sts dependency");
        }
    }

    /**
     * The aws-iam login, which signs its request with the credentials of the AWS SDK default chain: environment
     * variables, system properties, web identity token (such as IAM roles for service accounts on EKS, with the sts
     * module of the AWS SDK), profile (with the sso and ssooidc modules for an SSO profile), container (ECS) or
     * instance profile (EC2).
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
                .credentialsProvider(credentials);
        if (ObjectHelper.isNotEmpty(settings.getRole())) {
            // without a role, Vault takes the role named as the IAM principal
            builder.role(settings.getRole());
        }
        if (ObjectHelper.isNotEmpty(settings.getAwsRegion())) {
            builder.region(Region.of(settings.getAwsRegion()));
        } else if (ObjectHelper.isEmpty(settings.getAwsStsEndpoint())) {
            // the global endpoint takes requests signed for us-east-1, whatever the region of the application
            builder.region(Region.US_EAST_1);
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
