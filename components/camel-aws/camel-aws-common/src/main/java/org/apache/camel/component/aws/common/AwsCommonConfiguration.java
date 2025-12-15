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

import software.amazon.awssdk.core.Protocol;

/**
 * Common configuration interface for all AWS components.
 * <p>
 * Each AWS component's Configuration class should implement this interface to enable use of the common
 * {@link AwsClientBuilderUtil} for client creation.
 * </p>
 */
public interface AwsCommonConfiguration {

    // ==================== Credentials - Static ====================

    /**
     * Amazon AWS Access Key.
     */
    String getAccessKey();

    /**
     * Amazon AWS Secret Key.
     */
    String getSecretKey();

    /**
     * Amazon AWS Session Token used when the user needs to assume an IAM role.
     */
    String getSessionToken();

    // ==================== Credentials - Provider Selection ====================

    /**
     * Set whether the client should expect to load credentials through a default credentials provider.
     */
    boolean isUseDefaultCredentialsProvider();

    /**
     * Set whether the client should expect to load credentials through a profile credentials provider.
     */
    boolean isUseProfileCredentialsProvider();

    /**
     * Set whether the client should expect to use Session Credentials. This is useful in a situation in which the user
     * needs to assume an IAM role.
     */
    boolean isUseSessionCredentials();

    /**
     * If using a profile credentials provider, this parameter specifies the profile name.
     */
    String getProfileCredentialsName();

    // ==================== Proxy Configuration ====================

    /**
     * To define a proxy protocol when instantiating the client.
     */
    Protocol getProxyProtocol();

    /**
     * To define a proxy host when instantiating the client.
     */
    String getProxyHost();

    /**
     * Specify a proxy port to be used inside the client definition.
     */
    Integer getProxyPort();

    // ==================== Region and Endpoint ====================

    /**
     * The region in which the client needs to work. When using this parameter, the configuration will expect the
     * lowercase name of the region (for example, ap-east-1).
     */
    String getRegion();

    /**
     * Set the need for overriding the endpoint. This option needs to be used in combination with the
     * uriEndpointOverride option.
     */
    boolean isOverrideEndpoint();

    /**
     * Set the overriding URI endpoint. This option needs to be used in combination with overrideEndpoint option.
     */
    String getUriEndpointOverride();

    // ==================== Security ====================

    /**
     * If we want to trust all certificates in case of overriding the endpoint.
     */
    boolean isTrustAllCertificates();
}
