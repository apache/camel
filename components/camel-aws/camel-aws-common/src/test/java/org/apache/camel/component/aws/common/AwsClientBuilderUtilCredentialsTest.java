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

import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.core.Protocol;
import software.amazon.awssdk.services.sts.StsAsyncClient;
import software.amazon.awssdk.services.sts.StsClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AwsClientBuilderUtilCredentialsTest {

    @Test
    void sessionCredentialsAreUsed() {
        TestConfiguration config = new TestConfiguration();
        config.useSessionCredentials = true;
        config.accessKey = "accessKey";
        config.secretKey = "secretKey";
        config.sessionToken = "sessionToken";

        AwsCredentialsProvider provider = AwsClientBuilderUtil.resolveCredentialsProvider(config);

        assertThat(provider.resolveCredentials()).isInstanceOfSatisfying(AwsSessionCredentials.class, credentials -> {
            assertThat(credentials.accessKeyId()).isEqualTo("accessKey");
            assertThat(credentials.secretAccessKey()).isEqualTo("secretKey");
            assertThat(credentials.sessionToken()).isEqualTo("sessionToken");
        });
    }

    @Test
    void sessionCredentialsWithoutKeysFail() {
        TestConfiguration config = new TestConfiguration();
        config.useSessionCredentials = true;

        assertThatThrownBy(() -> AwsClientBuilderUtil.resolveCredentialsProvider(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith(
                        "useSessionCredentials is set to true, but accessKey, secretKey, sessionToken are not set");
    }

    @Test
    void sessionCredentialsWithoutSessionTokenFail() {
        TestConfiguration config = new TestConfiguration();
        config.useSessionCredentials = true;
        config.accessKey = "accessKey";
        config.secretKey = "secretKey";

        // before, the keys were used as static credentials without the session token
        assertThatThrownBy(() -> AwsClientBuilderUtil.resolveCredentialsProvider(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("useSessionCredentials is set to true, but sessionToken is not set");
    }

    @Test
    void sessionCredentialsWithEmptySecretKeyFail() {
        TestConfiguration config = new TestConfiguration();
        config.useSessionCredentials = true;
        config.accessKey = "accessKey";
        config.secretKey = "";
        config.sessionToken = "sessionToken";

        assertThatThrownBy(() -> AwsClientBuilderUtil.resolveCredentialsProvider(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("useSessionCredentials is set to true, but secretKey is not set");
    }

    @Test
    void buildClientFailsWithSessionCredentialsWithoutKeys() {
        TestConfiguration config = new TestConfiguration();
        config.useSessionCredentials = true;
        config.region = "us-east-1";

        assertThatThrownBy(() -> AwsClientBuilderUtil.buildClient(config, StsClient::builder))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("useSessionCredentials");
        assertThatThrownBy(() -> AwsClientBuilderUtil.buildAsyncClient(config, StsAsyncClient::builder))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("useSessionCredentials");
    }

    @Test
    void staticCredentialsAreUsed() {
        TestConfiguration config = new TestConfiguration();
        config.accessKey = "accessKey";
        config.secretKey = "secretKey";

        AwsCredentialsProvider provider = AwsClientBuilderUtil.resolveCredentialsProvider(config);

        assertThat(provider.resolveCredentials()).isInstanceOfSatisfying(AwsBasicCredentials.class, credentials -> {
            assertThat(credentials.accessKeyId()).isEqualTo("accessKey");
            assertThat(credentials.secretAccessKey()).isEqualTo("secretKey");
        });
    }

    @Test
    void accessKeyWithoutSecretKeyFails() {
        TestConfiguration config = new TestConfiguration();
        config.accessKey = "accessKey";

        assertThatThrownBy(() -> AwsClientBuilderUtil.resolveCredentialsProvider(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("accessKey is set, but secretKey is not set");
    }

    @Test
    void secretKeyWithoutAccessKeyFails() {
        TestConfiguration config = new TestConfiguration();
        config.accessKey = "";
        config.secretKey = "secretKey";

        assertThatThrownBy(() -> AwsClientBuilderUtil.resolveCredentialsProvider(config))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageStartingWith("secretKey is set, but accessKey is not set");
    }

    @Test
    void noCredentialsUseTheSdkDefaultChain() {
        TestConfiguration config = new TestConfiguration();

        assertThat(AwsClientBuilderUtil.resolveCredentialsProvider(config)).isNull();

        config.accessKey = "";
        config.secretKey = "";
        assertThat(AwsClientBuilderUtil.resolveCredentialsProvider(config)).isNull();
    }

    @Test
    void defaultAndProfileCredentialsProviderTakePrecedence() {
        TestConfiguration config = new TestConfiguration();
        config.useSessionCredentials = true;
        config.useDefaultCredentialsProvider = true;

        AwsCredentialsProvider provider = AwsClientBuilderUtil.resolveCredentialsProvider(config);
        try {
            assertThat(provider).isInstanceOf(DefaultCredentialsProvider.class);
        } finally {
            ((DefaultCredentialsProvider) provider).close();
        }

        config.useDefaultCredentialsProvider = false;
        config.useProfileCredentialsProvider = true;
        provider = AwsClientBuilderUtil.resolveCredentialsProvider(config);
        try {
            assertThat(provider).isInstanceOf(ProfileCredentialsProvider.class);
        } finally {
            ((ProfileCredentialsProvider) provider).close();
        }
    }

    private static final class TestConfiguration implements AwsCommonConfiguration {

        private String accessKey;
        private String secretKey;
        private String sessionToken;
        private boolean useDefaultCredentialsProvider;
        private boolean useProfileCredentialsProvider;
        private boolean useSessionCredentials;
        private String region;

        @Override
        public String getAccessKey() {
            return accessKey;
        }

        @Override
        public String getSecretKey() {
            return secretKey;
        }

        @Override
        public String getSessionToken() {
            return sessionToken;
        }

        @Override
        public boolean isUseDefaultCredentialsProvider() {
            return useDefaultCredentialsProvider;
        }

        @Override
        public boolean isUseProfileCredentialsProvider() {
            return useProfileCredentialsProvider;
        }

        @Override
        public boolean isUseSessionCredentials() {
            return useSessionCredentials;
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
            return region;
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
