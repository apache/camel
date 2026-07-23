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
package org.apache.camel.component.platform.http;

import org.apache.camel.Exchange;
import org.apache.camel.http.base.OAuthHttpSecuritySupport;

public final class PlatformHttpConstants {

    public static final String PLATFORM_HTTP_COMPONENT_NAME = "platform-http";
    public static final String PLATFORM_HTTP_ENGINE_NAME = "platform-http-engine";
    public static final String PLATFORM_HTTP_ENGINE_FACTORY = "platform-http-engine";
    /**
     * Exchange property containing the OAuth token validation result for successfully authenticated HTTP consumer
     * requests. This intentionally does not use {@link Exchange#AUTHENTICATION}, so OAuth bearer-token validation does
     * not overwrite or conflict with other Camel authentication mechanisms.
     */
    public static final String OAUTH_TOKEN_VALIDATION_RESULT = OAuthHttpSecuritySupport.OAUTH_TOKEN_VALIDATION_RESULT;

    private PlatformHttpConstants() {
    }
}
