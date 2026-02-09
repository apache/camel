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

import java.net.URI;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.awscore.client.builder.AwsClientBuilder;
import software.amazon.awssdk.core.SdkClient;
import software.amazon.awssdk.core.client.builder.SdkAsyncClientBuilder;
import software.amazon.awssdk.core.client.builder.SdkSyncClientBuilder;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.SdkHttpConfigurationOption;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.http.apache.ProxyConfiguration;
import software.amazon.awssdk.http.async.SdkAsyncHttpClient;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.utils.AttributeMap;

/**
 * Utility class for building AWS SDK clients with common configuration.
 * <p>
 * This class eliminates code duplication across all AWS Camel components by providing a centralized way to configure
 * AWS clients with credentials, proxy, region, endpoint, and other common settings.
 * </p>
 */
public final class AwsClientBuilderUtil {

    private static final Logger LOG = LoggerFactory.getLogger(AwsClientBuilderUtil.class);

    private AwsClientBuilderUtil() {
    }

    /**
     * Build an AWS client with the given configuration.
     *
     * @param  config                The common AWS configuration
     * @param  builderSupplier       Supplier for the service-specific client builder (e.g., S3Client::builder)
     * @param  serviceSpecificConfig Optional consumer for service-specific configuration (e.g., S3's forcePathStyle)
     * @param  <B>                   The builder type (must extend both AwsClientBuilder and SdkSyncClientBuilder)
     * @param  <C>                   The client type
     * @return                       The configured AWS client
     */
    @SuppressWarnings("unchecked")
    public static <B extends AwsClientBuilder<B, C> & SdkSyncClientBuilder<B, C>, C extends SdkClient> C buildClient(
            AwsCommonConfiguration config,
            Supplier<B> builderSupplier,
            Consumer<B> serviceSpecificConfig) {

        B clientBuilder = builderSupplier.get();
        ApacheHttpClient.Builder httpClientBuilder = null;
        boolean httpClientConfigured = false;

        // 1. Configure proxy
        if (ObjectHelper.isNotEmpty(config.getProxyHost())
                && ObjectHelper.isNotEmpty(config.getProxyPort())) {
            LOG.trace("Configuring proxy: {}:{}", config.getProxyHost(), config.getProxyPort());
            URI proxyEndpoint = URI.create(
                    config.getProxyProtocol() + "://" + config.getProxyHost() + ":" + config.getProxyPort());
            ProxyConfiguration proxyConfig = ProxyConfiguration.builder()
                    .endpoint(proxyEndpoint)
                    .build();
            httpClientBuilder = ApacheHttpClient.builder().proxyConfiguration(proxyConfig);
            httpClientConfigured = true;
        }

        // 2. Configure credentials
        AwsCredentialsProvider credentialsProvider = resolveCredentialsProvider(config);
        if (credentialsProvider != null) {
            clientBuilder.credentialsProvider(credentialsProvider);
        }

        // 3. Apply HTTP client builder if configured (before trust all certs check)
        if (httpClientConfigured) {
            clientBuilder.httpClientBuilder(httpClientBuilder);
        }

        // 4. Configure region
        if (ObjectHelper.isNotEmpty(config.getRegion())) {
            clientBuilder.region(Region.of(config.getRegion()));
        }

        // 5. Configure endpoint override
        if (config.isOverrideEndpoint() && ObjectHelper.isNotEmpty(config.getUriEndpointOverride())) {
            clientBuilder.endpointOverride(URI.create(config.getUriEndpointOverride()));
        }

        // 6. Configure trust all certificates
        if (config.isTrustAllCertificates()) {
            if (httpClientBuilder == null) {
                httpClientBuilder = ApacheHttpClient.builder();
            }
            SdkHttpClient httpClient = httpClientBuilder.buildWithDefaults(
                    AttributeMap.builder()
                            .put(SdkHttpConfigurationOption.TRUST_ALL_CERTIFICATES, Boolean.TRUE)
                            .build());
            clientBuilder.httpClient(httpClient);
            clientBuilder.httpClientBuilder(null);
        }

        // 7. Apply service-specific configuration
        if (serviceSpecificConfig != null) {
            serviceSpecificConfig.accept(clientBuilder);
        }

        return clientBuilder.build();
    }

    /**
     * Build an AWS client with the given configuration, without service-specific configuration.
     *
     * @param  config          The common AWS configuration
     * @param  builderSupplier Supplier for the service-specific client builder
     * @param  <B>             The builder type
     * @param  <C>             The client type
     * @return                 The configured AWS client
     */
    public static <B extends AwsClientBuilder<B, C> & SdkSyncClientBuilder<B, C>, C extends SdkClient> C buildClient(
            AwsCommonConfiguration config,
            Supplier<B> builderSupplier) {
        return buildClient(config, builderSupplier, null);
    }

    /**
     * Build an AWS async client with the given configuration.
     *
     * @param  config                The common AWS configuration
     * @param  builderSupplier       Supplier for the service-specific async client builder (e.g.,
     *                               KinesisAsyncClient::builder)
     * @param  serviceSpecificConfig Optional consumer for service-specific configuration
     * @param  <B>                   The builder type (must extend both AwsClientBuilder and SdkAsyncClientBuilder)
     * @param  <C>                   The client type
     * @return                       The configured AWS async client
     */
    @SuppressWarnings("unchecked")
    public static <B extends AwsClientBuilder<B, C> & SdkAsyncClientBuilder<B, C>, C extends SdkClient> C buildAsyncClient(
            AwsCommonConfiguration config,
            Supplier<B> builderSupplier,
            Consumer<B> serviceSpecificConfig) {

        B clientBuilder = builderSupplier.get();
        NettyNioAsyncHttpClient.Builder httpClientBuilder = null;
        boolean httpClientConfigured = false;

        // 1. Configure proxy
        if (ObjectHelper.isNotEmpty(config.getProxyHost())
                && ObjectHelper.isNotEmpty(config.getProxyPort())) {
            LOG.trace("Configuring async proxy: {}:{}", config.getProxyHost(), config.getProxyPort());
            software.amazon.awssdk.http.nio.netty.ProxyConfiguration proxyConfig
                    = software.amazon.awssdk.http.nio.netty.ProxyConfiguration.builder()
                            .scheme(config.getProxyProtocol().toString())
                            .host(config.getProxyHost())
                            .port(config.getProxyPort())
                            .build();
            httpClientBuilder = NettyNioAsyncHttpClient.builder().proxyConfiguration(proxyConfig);
            httpClientConfigured = true;
        }

        // 2. Configure credentials
        AwsCredentialsProvider credentialsProvider = resolveCredentialsProvider(config);
        if (credentialsProvider != null) {
            clientBuilder.credentialsProvider(credentialsProvider);
        }

        // 3. Apply HTTP client builder if configured (before trust all certs check)
        if (httpClientConfigured) {
            clientBuilder.httpClientBuilder(httpClientBuilder);
        }

        // 4. Configure region
        if (ObjectHelper.isNotEmpty(config.getRegion())) {
            clientBuilder.region(Region.of(config.getRegion()));
        }

        // 5. Configure endpoint override
        if (config.isOverrideEndpoint() && ObjectHelper.isNotEmpty(config.getUriEndpointOverride())) {
            clientBuilder.endpointOverride(URI.create(config.getUriEndpointOverride()));
        }

        // 6. Configure trust all certificates
        if (config.isTrustAllCertificates()) {
            if (httpClientBuilder == null) {
                httpClientBuilder = NettyNioAsyncHttpClient.builder();
            }
            SdkAsyncHttpClient asyncHttpClient = httpClientBuilder.buildWithDefaults(
                    AttributeMap.builder()
                            .put(SdkHttpConfigurationOption.TRUST_ALL_CERTIFICATES, Boolean.TRUE)
                            .build());
            clientBuilder.httpClient(asyncHttpClient);
            clientBuilder.httpClientBuilder(null);
        }

        // 7. Apply service-specific configuration
        if (serviceSpecificConfig != null) {
            serviceSpecificConfig.accept(clientBuilder);
        }

        return clientBuilder.build();
    }

    /**
     * Build an AWS async client with the given configuration, without service-specific configuration.
     *
     * @param  config          The common AWS configuration
     * @param  builderSupplier Supplier for the service-specific async client builder
     * @param  <B>             The builder type
     * @param  <C>             The client type
     * @return                 The configured AWS async client
     */
    public static <B extends AwsClientBuilder<B, C> & SdkAsyncClientBuilder<B, C>, C extends SdkClient> C buildAsyncClient(
            AwsCommonConfiguration config,
            Supplier<B> builderSupplier) {
        return buildAsyncClient(config, builderSupplier, null);
    }

    /**
     * Resolve the appropriate credentials provider based on configuration.
     * <p>
     * The priority order is:
     * <ol>
     * <li>Default credentials provider (IAM roles, environment variables, etc.)</li>
     * <li>Profile credentials provider</li>
     * <li>Session credentials (temporary credentials with session token)</li>
     * <li>Static credentials (access key + secret key)</li>
     * </ol>
     * </p>
     *
     * @param  config The AWS configuration
     * @return        The resolved credentials provider, or null to use SDK default chain
     */
    private static AwsCredentialsProvider resolveCredentialsProvider(AwsCommonConfiguration config) {

        // Priority 1: Default credentials provider (IAM roles, env vars, etc.)
        if (config.isUseDefaultCredentialsProvider()) {
            LOG.trace("Using default credentials provider (IAM)");
            return DefaultCredentialsProvider.create();
        }

        // Priority 2: Profile credentials provider
        if (config.isUseProfileCredentialsProvider()) {
            String profileName = config.getProfileCredentialsName();
            LOG.trace("Using profile credentials provider: {}", profileName);
            if (ObjectHelper.isNotEmpty(profileName)) {
                return ProfileCredentialsProvider.create(profileName);
            }
            return ProfileCredentialsProvider.create();
        }

        // Priority 3: Session credentials (temporary credentials with session token)
        if (config.isUseSessionCredentials()) {
            if (ObjectHelper.isNotEmpty(config.getAccessKey())
                    && ObjectHelper.isNotEmpty(config.getSecretKey())
                    && ObjectHelper.isNotEmpty(config.getSessionToken())) {
                LOG.trace("Using session credentials");
                AwsSessionCredentials sessionCredentials = AwsSessionCredentials.create(
                        config.getAccessKey(),
                        config.getSecretKey(),
                        config.getSessionToken());
                return StaticCredentialsProvider.create(sessionCredentials);
            }
        }

        // Priority 4: Static credentials (access key + secret key)
        if (ObjectHelper.isNotEmpty(config.getAccessKey())
                && ObjectHelper.isNotEmpty(config.getSecretKey())) {
            LOG.trace("Using static credentials");
            AwsBasicCredentials basicCredentials = AwsBasicCredentials.create(
                    config.getAccessKey(),
                    config.getSecretKey());
            return StaticCredentialsProvider.create(basicCredentials);
        }

        // No explicit credentials - let SDK use its default chain
        LOG.trace("No explicit credentials configured, using SDK default chain");
        return null;
    }
}
