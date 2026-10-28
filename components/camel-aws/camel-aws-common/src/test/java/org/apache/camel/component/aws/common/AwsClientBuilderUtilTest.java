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
package org.apache.camel.component.aws.common;

import java.util.ServiceConfigurationError;

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.Protocol;
import software.amazon.awssdk.core.SdkSystemSetting;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sts.StsAsyncClient;
import software.amazon.awssdk.services.sts.StsClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The test resources register {@link ForeignHttpService} as a sync and an async HTTP client implementation of the AWS
 * SDK, which it is not. The SDK lookup of the HTTP client on the classpath then fails, as when a Kafka Connect plugin
 * sees the registrations of the AWS SDK of the worker.
 */
class AwsClientBuilderUtilTest {

    private static final AwsCommonConfiguration CONFIGURATION = new StaticCredentialsConfiguration();

    @Test
    void sdkLookupFailsOnTheForeignRegistration() {
        StaticCredentialsProvider credentials
                = StaticCredentialsProvider.create(AwsBasicCredentials.create("accessKey", "secretKey"));

        assertThatThrownBy(() -> StsClient.builder().region(Region.US_EAST_1).credentialsProvider(credentials).build())
                .isInstanceOf(ServiceConfigurationError.class)
                .hasMessageContaining("not a subtype");
        assertThatThrownBy(() -> StsAsyncClient.builder().region(Region.US_EAST_1).credentialsProvider(credentials).build())
                .isInstanceOf(ServiceConfigurationError.class)
                .hasMessageContaining("not a subtype");
    }

    @Test
    void buildClientDoesNotUseTheSdkLookup() {
        try (StsClient client = AwsClientBuilderUtil.buildClient(CONFIGURATION, StsClient::builder)) {
            assertThat(client).isNotNull();
        }
    }

    @Test
    void buildAsyncClientDoesNotUseTheSdkLookup() {
        try (StsAsyncClient client = AwsClientBuilderUtil.buildAsyncClient(CONFIGURATION, StsAsyncClient::builder)) {
            assertThat(client).isNotNull();
        }
    }

    @Test
    void sdkSettingLeavesTheChoiceToTheSdk() {
        // the SDK fails to load the implementation the setting names, so Camel did not set its own HTTP client
        String property = SdkSystemSetting.SYNC_HTTP_SERVICE_IMPL.property();
        System.setProperty(property, "org.apache.camel.component.aws.common.MissingHttpService");
        try {
            assertThatThrownBy(() -> AwsClientBuilderUtil.buildClient(CONFIGURATION, StsClient::builder))
                    .isInstanceOf(SdkClientException.class)
                    .hasMessageContaining(property);
        } finally {
            System.clearProperty(property);
        }
    }

    @Test
    void sdkAsyncSettingLeavesTheChoiceToTheSdk() {
        String property = SdkSystemSetting.ASYNC_HTTP_SERVICE_IMPL.property();
        System.setProperty(property, "org.apache.camel.component.aws.common.MissingHttpService");
        try {
            assertThatThrownBy(() -> AwsClientBuilderUtil.buildAsyncClient(CONFIGURATION, StsAsyncClient::builder))
                    .isInstanceOf(SdkClientException.class)
                    .hasMessageContaining("MissingHttpService");
        } finally {
            System.clearProperty(property);
        }
    }

    /**
     * Registered in the test resources as an SdkHttpService and an SdkAsyncHttpService, without being either.
     */
    public static class ForeignHttpService {
    }

    private static final class StaticCredentialsConfiguration implements AwsCommonConfiguration {

        @Override
        public String getAccessKey() {
            return "accessKey";
        }

        @Override
        public String getSecretKey() {
            return "secretKey";
        }

        @Override
        public String getSessionToken() {
            return null;
        }

        @Override
        public boolean isUseDefaultCredentialsProvider() {
            return false;
        }

        @Override
        public boolean isUseProfileCredentialsProvider() {
            return false;
        }

        @Override
        public boolean isUseSessionCredentials() {
            return false;
        }

        @Override
        public String getProfileCredentialsName() {
            return null;
        }

        @Override
        public Protocol getProxyProtocol() {
            return null;
        }

        @Override
        public String getProxyHost() {
            return null;
        }

        @Override
        public Integer getProxyPort() {
            return null;
        }

        @Override
        public String getRegion() {
            return Region.US_EAST_1.id();
        }

        @Override
        public boolean isOverrideEndpoint() {
            return false;
        }

        @Override
        public String getUriEndpointOverride() {
            return null;
        }

        @Override
        public boolean isTrustAllCertificates() {
            return false;
        }
    }
}
