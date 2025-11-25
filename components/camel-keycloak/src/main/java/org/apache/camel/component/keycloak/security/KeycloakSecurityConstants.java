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
package org.apache.camel.component.keycloak.security;

public final class KeycloakSecurityConstants {

    public static final String ACCESS_TOKEN_HEADER = "CamelKeycloakAccessToken";
    public static final String ACCESS_TOKEN_PROPERTY = "CamelKeycloakAccessToken";
    public static final String REFRESH_TOKEN_HEADER = "CamelKeycloakRefreshToken";
    public static final String REFRESH_TOKEN_PROPERTY = "CamelKeycloakRefreshToken";
    public static final String USER_INFO_HEADER = "CamelKeycloakUserInfo";
    public static final String USER_INFO_PROPERTY = "CamelKeycloakUserInfo";
    public static final String USER_ROLES_HEADER = "CamelKeycloakUserRoles";
    public static final String USER_ROLES_PROPERTY = "CamelKeycloakUserRoles";

    /**
     * Session ID property for binding tokens to specific sessions (prevents session fixation)
     */
    public static final String SESSION_ID_PROPERTY = "CamelKeycloakSessionId";

    /**
     * Token thumbprint property for validating token integrity (SHA-256 hash)
     */
    public static final String TOKEN_THUMBPRINT_PROPERTY = "CamelKeycloakTokenThumbprint";

    /**
     * Token subject (user ID) for binding validation
     */
    public static final String TOKEN_SUBJECT_PROPERTY = "CamelKeycloakTokenSubject";

    private KeycloakSecurityConstants() {
        // Utility class
    }
}
