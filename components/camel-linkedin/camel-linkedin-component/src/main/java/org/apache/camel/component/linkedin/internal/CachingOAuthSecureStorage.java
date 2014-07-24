/**
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
package org.apache.camel.component.linkedin.internal;

import org.apache.camel.component.linkedin.api.OAuthSecureStorage;
import org.apache.camel.component.linkedin.api.OAuthToken;

/**
 * Caching implementation of {@link org.apache.camel.component.linkedin.api.OAuthSecureStorage}
 */
public class CachingOAuthSecureStorage implements OAuthSecureStorage {

    private final OAuthSecureStorage secureStorage;
    private OAuthToken token;

    public CachingOAuthSecureStorage(OAuthSecureStorage secureStorage) {
        this.secureStorage = secureStorage;
    }

    @Override
    public OAuthToken getOAuthToken() {
        // delegate only if token doesn't exist or has expired
        if (secureStorage != null && (token == null || token.getExpiryTime() < System.currentTimeMillis())) {
            token = secureStorage.getOAuthToken();
        }
        return token;
    }

    @Override
    public void saveOAuthToken(OAuthToken newToken) {
        token = newToken;
        if (secureStorage != null) {
            secureStorage.saveOAuthToken(newToken);
        }
    }
}
