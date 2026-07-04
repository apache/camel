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
package org.apache.camel.http.base;

/**
 * Contract for HTTP endpoints that support incoming OAuth bearer-token validation.
 * <p/>
 * Note that the platform-http component does not implement this interface: its {@code oauthProfile} support is wired
 * through the platform-http engine SPI ({@code PlatformHttpSecurityHandler}) so platform runtimes can install
 * validation in their native HTTP/security layer.
 *
 * @since 4.21
 */
public interface OAuthProfileAwareHttpEndpoint {

    /**
     * Gets the configured OAuth profile name.
     *
     * @return the OAuth profile name, or null when OAuth validation is not configured
     */
    String getOauthProfile();

    /**
     * Gets the initialized OAuth HTTP security support.
     *
     * @return the support instance, or null before endpoint initialization or when OAuth validation is not configured
     */
    OAuthHttpSecuritySupport getOauthHttpSecurity();
}
