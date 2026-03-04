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
package org.apache.camel.component.google.common;

import java.util.Collection;

/**
 * Common configuration interface for all Google components.
 * <p>
 * Each Google component's Configuration class should implement this interface to enable use of the common
 * {@link GoogleCredentialsHelper} for credential creation.
 * </p>
 */
public interface GoogleCommonConfiguration {

    // ==================== Service Account Credentials ====================

    /**
     * Service account key file (JSON) for authenticating with Google services. Can be loaded from classpath, file
     * system, or http(s) URL. Prefixes: classpath:, file:, http:, https:
     */
    String getServiceAccountKey();

    // ==================== OAuth 2.0 Credentials (for legacy API client) ====================

    /**
     * OAuth 2.0 Client ID for the Google application.
     */
    default String getClientId() {
        return null;
    }

    /**
     * OAuth 2.0 Client Secret for the Google application.
     */
    default String getClientSecret() {
        return null;
    }

    /**
     * OAuth 2.0 access token. This typically expires after an hour so refreshToken is recommended for long term usage.
     */
    default String getAccessToken() {
        return null;
    }

    /**
     * OAuth 2.0 refresh token. Using this, the component can obtain a new accessToken whenever the current one expires.
     */
    default String getRefreshToken() {
        return null;
    }

    // ==================== Domain-wide Delegation ====================

    /**
     * User email to impersonate for domain-wide delegation with service account.
     */
    default String getDelegate() {
        return null;
    }

    // ==================== Scopes ====================

    /**
     * Returns the OAuth scopes as a collection. Used when creating credentials.
     */
    default Collection<String> getScopesAsList() {
        return null;
    }

    // ==================== Authentication Toggle ====================

    /**
     * Whether to authenticate with Google services. Set to false when using emulators (e.g., PubSub emulator).
     */
    default boolean isAuthenticate() {
        return true;
    }
}
