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
package org.apache.camel.component.cyberark.vault.client;

import org.apache.camel.component.cyberark.vault.client.impl.ConjurClientImpl;

/**
 * Factory for creating CyberArk Conjur clients
 */
public final class ConjurClientFactory {

    private ConjurClientFactory() {
    }

    /**
     * Create a Conjur client using username and password authentication
     *
     * @param  url      the Conjur server URL
     * @param  account  the Conjur account name
     * @param  username the username for authentication
     * @param  password the password for authentication
     * @return          a configured ConjurClient instance
     */
    public static ConjurClient createWithCredentials(String url, String account, String username, String password) {
        return new ConjurClientImpl(url, account, username, password, null, null);
    }

    /**
     * Create a Conjur client using API key authentication
     *
     * @param  url      the Conjur server URL
     * @param  account  the Conjur account name
     * @param  username the username (login) for the API key
     * @param  apiKey   the API key for authentication
     * @return          a configured ConjurClient instance
     */
    public static ConjurClient createWithApiKey(String url, String account, String username, String apiKey) {
        return new ConjurClientImpl(url, account, username, null, apiKey, null);
    }

    /**
     * Create a Conjur client using a pre-authenticated token
     *
     * @param  url       the Conjur server URL
     * @param  account   the Conjur account name
     * @param  authToken the pre-authenticated access token
     * @return           a configured ConjurClient instance
     */
    public static ConjurClient createWithToken(String url, String account, String authToken) {
        return new ConjurClientImpl(url, account, null, null, null, authToken);
    }
}
