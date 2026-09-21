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

import java.io.File;

import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProviderChain;
import software.amazon.awssdk.auth.credentials.ContainerCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.SystemPropertyCredentialsProvider;

/**
 * Detects which AWS credentials source applies to the current runtime - JVM system properties, environment variables,
 * web identity / IRSA, a shared profile, or ECS / EKS Pod Identity container credentials - selects the matching
 * provider, and reports the chosen source at INFO.
 * <p>
 * This is an opt-in enhancement over the SDK {@link DefaultCredentialsProvider} whose purpose is observability. The SDK
 * chain already resolves credentials in the same order used here, so the selected source never differs from the SDK;
 * this class simply makes the resolved source visible in the logs and returns it as a targeted provider. When no source
 * is recognised it returns {@code null} so the caller falls back to the SDK default chain (which also covers EC2
 * instance metadata).
 * </p>
 * <p>
 * The detected provider is returned as the head of a chain whose tail is the full {@link DefaultCredentialsProvider},
 * so a detected-but-unusable source (for example a profile without resolvable credentials) still falls back to the SDK
 * default chain rather than failing.
 * </p>
 *
 * @since 4.23
 */
public final class AwsRuntimeCredentialsResolver {

    static final String ENV_ACCESS_KEY = "AWS_ACCESS_KEY_ID";
    static final String ENV_SECRET_KEY = "AWS_SECRET_ACCESS_KEY";
    static final String ENV_WEB_IDENTITY_TOKEN_FILE = "AWS_WEB_IDENTITY_TOKEN_FILE";
    static final String ENV_ROLE_ARN = "AWS_ROLE_ARN";
    static final String ENV_CONTAINER_RELATIVE_URI = "AWS_CONTAINER_CREDENTIALS_RELATIVE_URI";
    static final String ENV_CONTAINER_FULL_URI = "AWS_CONTAINER_CREDENTIALS_FULL_URI";
    static final String ENV_PROFILE = "AWS_PROFILE";

    static final String SYS_ACCESS_KEY = "aws.accessKeyId";
    static final String SYS_SECRET_KEY = "aws.secretAccessKey";

    private static final Logger LOG = LoggerFactory.getLogger(AwsRuntimeCredentialsResolver.class);

    private AwsRuntimeCredentialsResolver() {
    }

    /**
     * The credentials source detected for the current runtime.
     */
    public enum Source {
        SYSTEM_PROPERTY("JVM system properties (aws.accessKeyId/aws.secretAccessKey)"),
        ENVIRONMENT("environment variables (AWS_ACCESS_KEY_ID/AWS_SECRET_ACCESS_KEY)"),
        WEB_IDENTITY("web identity token / IRSA (AWS_WEB_IDENTITY_TOKEN_FILE)"),
        PROFILE("shared profile (AWS_PROFILE or ~/.aws/credentials)"),
        CONTAINER("container credentials (ECS task role / EKS Pod Identity)"),
        UNKNOWN("no recognised runtime");

        private final String description;

        Source(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    /**
     * Detect the current runtime and return the matching credentials provider, or {@code null} when no runtime can be
     * recognised (so the caller falls back to the SDK default credentials provider chain).
     *
     * @return the resolved credentials provider, or {@code null} to use the SDK default chain
     */
    public static AwsCredentialsProvider resolve() {
        return resolve(RuntimeEnvironment.SYSTEM);
    }

    static AwsCredentialsProvider resolve(RuntimeEnvironment environment) {
        Source source = detect(environment);

        switch (source) {
            case UNKNOWN:
                LOG.info("AWS credentials auto-detect: {} - using the SDK default credentials provider chain",
                        source.getDescription());
                return null;
            case WEB_IDENTITY:
                // Web identity (IRSA) requires software.amazon.awssdk:sts on the classpath to assume the role;
                // camel-aws-common does not pull sts, so delegate to the SDK default provider, which performs the
                // web-identity exchange when sts is present and degrades gracefully otherwise.
                LOG.info("AWS credentials auto-detect: detected {} - delegating to the SDK default credentials"
                         + " provider chain (web identity requires software.amazon.awssdk:sts on the classpath)",
                        source.getDescription());
                return DefaultCredentialsProvider.builder().build();
            default:
                LOG.info("AWS credentials auto-detect: detected {}", source.getDescription());
                return AwsCredentialsProviderChain.of(providerFor(source), DefaultCredentialsProvider.builder().build());
        }
    }

    private static AwsCredentialsProvider providerFor(Source source) {
        switch (source) {
            case SYSTEM_PROPERTY:
                return SystemPropertyCredentialsProvider.create();
            case ENVIRONMENT:
                return EnvironmentVariableCredentialsProvider.create();
            case PROFILE:
                return ProfileCredentialsProvider.create();
            case CONTAINER:
                return ContainerCredentialsProvider.builder().build();
            default:
                // WEB_IDENTITY and UNKNOWN are handled directly by resolve(...)
                return DefaultCredentialsProvider.builder().build();
        }
    }

    /**
     * Detect the runtime credentials source. The probe order mirrors the AWS SDK {@link DefaultCredentialsProvider}
     * chain (system properties, environment, web identity, profile, container) so the resolved source never differs
     * from the SDK. EC2 instance metadata is intentionally not probed here: a bare link-local check is not AWS-specific
     * (Azure and GCP answer on the same address) and would ignore {@code AWS_EC2_METADATA_DISABLED}, so EC2 is left to
     * the SDK default chain fallback.
     */
    static Source detect(RuntimeEnvironment environment) {
        if (ObjectHelper.isNotEmpty(environment.getProperty(SYS_ACCESS_KEY))
                && ObjectHelper.isNotEmpty(environment.getProperty(SYS_SECRET_KEY))) {
            return Source.SYSTEM_PROPERTY;
        }
        if (ObjectHelper.isNotEmpty(environment.getenv(ENV_ACCESS_KEY))
                && ObjectHelper.isNotEmpty(environment.getenv(ENV_SECRET_KEY))) {
            return Source.ENVIRONMENT;
        }
        if (ObjectHelper.isNotEmpty(environment.getenv(ENV_WEB_IDENTITY_TOKEN_FILE))
                && ObjectHelper.isNotEmpty(environment.getenv(ENV_ROLE_ARN))) {
            return Source.WEB_IDENTITY;
        }
        // Only an explicit AWS_PROFILE or the credentials file (not a bare ~/.aws/config, which often holds just a
        // region) is treated as a profile credential signal.
        if (ObjectHelper.isNotEmpty(environment.getenv(ENV_PROFILE))
                || environment.fileExists(awsFile(environment, "credentials"))) {
            return Source.PROFILE;
        }
        if (ObjectHelper.isNotEmpty(environment.getenv(ENV_CONTAINER_RELATIVE_URI))
                || ObjectHelper.isNotEmpty(environment.getenv(ENV_CONTAINER_FULL_URI))) {
            return Source.CONTAINER;
        }
        return Source.UNKNOWN;
    }

    private static String awsFile(RuntimeEnvironment environment, String name) {
        String home = environment.userHome();
        if (ObjectHelper.isEmpty(home)) {
            return null;
        }
        return home + File.separator + ".aws" + File.separator + name;
    }

    /**
     * Abstraction over the process environment so detection can be unit-tested deterministically.
     */
    interface RuntimeEnvironment {

        RuntimeEnvironment SYSTEM = new RuntimeEnvironment() {
            @Override
            public String getenv(String name) {
                return System.getenv(name);
            }

            @Override
            public String getProperty(String name) {
                return System.getProperty(name);
            }

            @Override
            public String userHome() {
                return System.getProperty("user.home");
            }

            @Override
            public boolean fileExists(String path) {
                return path != null && new File(path).isFile();
            }
        };

        String getenv(String name);

        String getProperty(String name);

        String userHome();

        boolean fileExists(String path);
    }
}
