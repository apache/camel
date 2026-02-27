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
package org.apache.camel.support;

import org.apache.camel.CamelContext;
import org.apache.camel.spi.OAuthClientAuthenticationFactory;

/**
 * Helper for resolving OAuth tokens via the {@link OAuthClientAuthenticationFactory} SPI.
 * <p/>
 * This requires camel-oauth on the classpath.
 */
public final class OAuthHelper {

    private OAuthHelper() {
    }

    /**
     * Resolves an OAuth bearer token using a named profile.
     * <p/>
     * The profile properties are resolved from {@code camel.oauth.<profileName>.*}.
     *
     * @param  context     the CamelContext
     * @param  profileName the OAuth profile name
     * @return             the bearer token string
     * @throws Exception   if the factory is not found or token acquisition fails
     */
    public static String resolveOAuthToken(CamelContext context, String profileName) throws Exception {
        OAuthClientAuthenticationFactory factory = ResolverHelper.resolveMandatoryService(
                context,
                OAuthClientAuthenticationFactory.FACTORY,
                OAuthClientAuthenticationFactory.class,
                "camel-oauth");
        return factory.resolveToken(context, profileName);
    }
}
