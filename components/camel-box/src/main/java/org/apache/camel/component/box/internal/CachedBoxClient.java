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
package org.apache.camel.component.box.internal;

import com.box.boxjavalibv2.BoxClient;
import org.apache.http.conn.ClientConnectionManager;

public class CachedBoxClient {

    private final BoxClient boxClient;

    private final String login;

    private final String clientId;

    private final CachingSecureStorage secureStorage;

    private final OAuthHelperListener listener;

    @SuppressWarnings("deprecation")
    private final ClientConnectionManager[] clientConnectionManager;

    @SuppressWarnings("deprecation")
    public CachedBoxClient(BoxClient boxClient, String login, String clientId, CachingSecureStorage secureStorage,
                           OAuthHelperListener listener, ClientConnectionManager[] clientConnectionManager) {
        this.boxClient = boxClient;
        this.login = login;
        this.clientId = clientId;
        this.secureStorage = secureStorage;
        this.listener = listener;
        if (clientConnectionManager == null || clientConnectionManager.length != 1) {
            throw new IllegalArgumentException("clientConnectionManager must be an array of length 1");
        }
        this.clientConnectionManager = clientConnectionManager;
    }

    public BoxClient getBoxClient() {
        return boxClient;
    }

    public CachingSecureStorage getSecureStorage() {
        return secureStorage;
    }

    public OAuthHelperListener getListener() {
        return listener;
    }

    @SuppressWarnings("deprecation")
    public ClientConnectionManager getClientConnectionManager() {
        return clientConnectionManager[0];
    }

    @Override
    public String toString() {
        return String.format("{login:%s, client_id:%s}", login, clientId);
    }
}
