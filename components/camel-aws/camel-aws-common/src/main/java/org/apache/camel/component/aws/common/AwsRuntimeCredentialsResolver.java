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
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Duration;

import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProviderChain;
import software.amazon.awssdk.auth.credentials.ContainerCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.auth.credentials.InstanceProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.SystemPropertyCredentialsProvider;

/**
 * Detects the runtime environment an AWS Camel component is running in - JVM system properties, environment variables,
 * web identity / IRSA, a shared profile, ECS / EKS Pod Identity container credentials, or EC2 instance metadata - and
 * returns the matching AWS credentials provider.
 * <p>
 * This is an opt-in enhancement over the SDK {@link DefaultCredentialsProvider}. The detection order deliberately
 * mirrors that provider's own chain, so the selected source never differs from the SDK; the environment is only probed
 * explicitly (with a short-timeout IMDS reachability check) so that off-EC2 workloads holding real credentials
 * (environment, profile or container) resolve without waiting on the SDK's IMDS-last lookup, and the chosen source is
 * reported at INFO.
 * </p>
 * <p>
 * The detected provider is returned as the head of a chain whose tail is the full {@link DefaultCredentialsProvider},
 * so a detected-but-unusable source (for example a profile file without resolvable credentials) still falls back to the
 * SDK default chain rather than failing.
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

    // IMDS link-local endpoint - probed with a short timeout so we never wait on the SDK's long IMDS-last lookup
    static final String IMDS_HOST = "169.254.169.254";
    static final int IMDS_PORT = 80;
    static final Duration IMDS_PROBE_TIMEOUT = Duration.ofMillis(500);

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
        PROFILE("shared profile (AWS_PROFILE or ~/.aws)"),
        CONTAINER("container credentials (ECS task role / EKS Pod Identity)"),
        EC2_INSTANCE("EC2 instance profile (IMDS)"),
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
                return DefaultCredentialsProvider.create();
            default:
                LOG.info("AWS credentials auto-detect: detected {}", source.getDescription());
                return AwsCredentialsProviderChain.of(providerFor(source), DefaultCredentialsProvider.create());
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
            case EC2_INSTANCE:
                return InstanceProfileCredentialsProvider.create();
            default:
                // WEB_IDENTITY and UNKNOWN are handled directly by resolve(...)
                return DefaultCredentialsProvider.create();
        }
    }

    /**
     * Detect the runtime environment. The probe order intentionally mirrors the AWS SDK
     * {@link DefaultCredentialsProvider} chain (system properties, environment, web identity, profile, container, EC2
     * instance) so the resolved source never differs from the SDK.
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
        if (ObjectHelper.isNotEmpty(environment.getenv(ENV_PROFILE))
                || environment.fileExists(awsFile(environment, "credentials"))
                || environment.fileExists(awsFile(environment, "config"))) {
            return Source.PROFILE;
        }
        if (ObjectHelper.isNotEmpty(environment.getenv(ENV_CONTAINER_RELATIVE_URI))
                || ObjectHelper.isNotEmpty(environment.getenv(ENV_CONTAINER_FULL_URI))) {
            return Source.CONTAINER;
        }
        if (environment.isImdsReachable(IMDS_PROBE_TIMEOUT)) {
            return Source.EC2_INSTANCE;
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

            @Override
            public boolean isImdsReachable(Duration timeout) {
                try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress(IMDS_HOST, IMDS_PORT), (int) timeout.toMillis());
                    return true;
                } catch (Exception e) {
                    return false;
                }
            }
        };

        String getenv(String name);

        String getProperty(String name);

        String userHome();

        boolean fileExists(String path);

        boolean isImdsReachable(Duration timeout);
    }
}
