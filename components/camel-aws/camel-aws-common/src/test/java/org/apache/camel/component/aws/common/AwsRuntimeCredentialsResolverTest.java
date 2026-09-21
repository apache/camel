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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.camel.component.aws.common.AwsRuntimeCredentialsResolver.RuntimeEnvironment;
import org.apache.camel.component.aws.common.AwsRuntimeCredentialsResolver.Source;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProviderChain;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;

import static org.assertj.core.api.Assertions.assertThat;

class AwsRuntimeCredentialsResolverTest {

    @Test
    void detectsSystemProperties() {
        FakeEnvironment env = new FakeEnvironment()
                .prop(AwsRuntimeCredentialsResolver.SYS_ACCESS_KEY, "AKIA")
                .prop(AwsRuntimeCredentialsResolver.SYS_SECRET_KEY, "secret");
        assertThat(AwsRuntimeCredentialsResolver.detect(env)).isEqualTo(Source.SYSTEM_PROPERTY);
    }

    @Test
    void detectsEnvironmentVariables() {
        FakeEnvironment env = new FakeEnvironment()
                .env(AwsRuntimeCredentialsResolver.ENV_ACCESS_KEY, "AKIA")
                .env(AwsRuntimeCredentialsResolver.ENV_SECRET_KEY, "secret");
        assertThat(AwsRuntimeCredentialsResolver.detect(env)).isEqualTo(Source.ENVIRONMENT);
    }

    @Test
    void detectsWebIdentity() {
        FakeEnvironment env = new FakeEnvironment()
                .env(AwsRuntimeCredentialsResolver.ENV_WEB_IDENTITY_TOKEN_FILE, "/var/run/secrets/token")
                .env(AwsRuntimeCredentialsResolver.ENV_ROLE_ARN, "arn:aws:iam::123456789012:role/app");
        assertThat(AwsRuntimeCredentialsResolver.detect(env)).isEqualTo(Source.WEB_IDENTITY);
    }

    @Test
    void detectsProfileFromEnv() {
        FakeEnvironment env = new FakeEnvironment().env(AwsRuntimeCredentialsResolver.ENV_PROFILE, "dev");
        assertThat(AwsRuntimeCredentialsResolver.detect(env)).isEqualTo(Source.PROFILE);
    }

    @Test
    void detectsProfileFromCredentialsFile() {
        FakeEnvironment env = new FakeEnvironment()
                .home("/home/tester")
                .file("/home/tester/.aws/credentials");
        assertThat(AwsRuntimeCredentialsResolver.detect(env)).isEqualTo(Source.PROFILE);
    }

    @Test
    void bareConfigFileIsNotAProfileSignal() {
        // ~/.aws/config often exists with only a region set - it must not be read as "profile credentials detected".
        FakeEnvironment env = new FakeEnvironment()
                .home("/home/tester")
                .file("/home/tester/.aws/config");
        assertThat(AwsRuntimeCredentialsResolver.detect(env)).isEqualTo(Source.UNKNOWN);
    }

    @Test
    void detectsContainer() {
        FakeEnvironment env = new FakeEnvironment()
                .env(AwsRuntimeCredentialsResolver.ENV_CONTAINER_FULL_URI, "http://169.254.170.23/v1/credentials");
        assertThat(AwsRuntimeCredentialsResolver.detect(env)).isEqualTo(Source.CONTAINER);
    }

    @Test
    void unknownWhenNothingDetected() {
        assertThat(AwsRuntimeCredentialsResolver.detect(new FakeEnvironment())).isEqualTo(Source.UNKNOWN);
    }

    @Test
    void mirrorsSdkOrderProfileBeforeContainer() {
        // The SDK DefaultCredentialsProvider probes the profile provider before the container provider - match it.
        FakeEnvironment env = new FakeEnvironment()
                .env(AwsRuntimeCredentialsResolver.ENV_PROFILE, "dev")
                .env(AwsRuntimeCredentialsResolver.ENV_CONTAINER_FULL_URI, "http://169.254.170.23/v1/credentials");
        assertThat(AwsRuntimeCredentialsResolver.detect(env)).isEqualTo(Source.PROFILE);
    }

    @Test
    void environmentTakesPrecedenceOverProfile() {
        FakeEnvironment env = new FakeEnvironment()
                .env(AwsRuntimeCredentialsResolver.ENV_ACCESS_KEY, "AKIA")
                .env(AwsRuntimeCredentialsResolver.ENV_SECRET_KEY, "secret")
                .env(AwsRuntimeCredentialsResolver.ENV_PROFILE, "dev");
        assertThat(AwsRuntimeCredentialsResolver.detect(env)).isEqualTo(Source.ENVIRONMENT);
    }

    @Test
    void resolveReturnsChainForConcreteSource() {
        FakeEnvironment env = new FakeEnvironment().env(AwsRuntimeCredentialsResolver.ENV_PROFILE, "dev");
        AwsCredentialsProvider provider = AwsRuntimeCredentialsResolver.resolve(env);
        assertThat(provider).isInstanceOf(AwsCredentialsProviderChain.class);
    }

    @Test
    void resolveDelegatesToDefaultForWebIdentity() {
        FakeEnvironment env = new FakeEnvironment()
                .env(AwsRuntimeCredentialsResolver.ENV_WEB_IDENTITY_TOKEN_FILE, "/var/run/secrets/token")
                .env(AwsRuntimeCredentialsResolver.ENV_ROLE_ARN, "arn:aws:iam::123456789012:role/app");
        AwsCredentialsProvider provider = AwsRuntimeCredentialsResolver.resolve(env);
        assertThat(provider).isInstanceOf(DefaultCredentialsProvider.class);
    }

    @Test
    void resolveReturnsNullWhenUnknown() {
        assertThat(AwsRuntimeCredentialsResolver.resolve(new FakeEnvironment())).isNull();
    }

    /**
     * Deterministic in-memory {@link RuntimeEnvironment} for the detection tests - no real env vars or files.
     */
    private static final class FakeEnvironment implements RuntimeEnvironment {
        private final Map<String, String> env = new HashMap<>();
        private final Map<String, String> props = new HashMap<>();
        private final Set<String> files = new HashSet<>();
        private String home = "/home/tester";

        FakeEnvironment env(String name, String value) {
            env.put(name, value);
            return this;
        }

        FakeEnvironment prop(String name, String value) {
            props.put(name, value);
            return this;
        }

        FakeEnvironment file(String path) {
            files.add(path);
            return this;
        }

        FakeEnvironment home(String value) {
            this.home = value;
            return this;
        }

        @Override
        public String getenv(String name) {
            return env.get(name);
        }

        @Override
        public String getProperty(String name) {
            return props.get(name);
        }

        @Override
        public String userHome() {
            return home;
        }

        @Override
        public boolean fileExists(String path) {
            return files.contains(path);
        }
    }
}
