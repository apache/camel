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

import com.box.boxjavalibv2.authorization.IAuthSecureStorage;
import com.box.boxjavalibv2.authorization.OAuthRefreshListener;
import com.box.boxjavalibv2.dao.IAuthData;

/**
* Wrapper implementation of {@link OAuthRefreshListener} that
 * delegates to an {@link IAuthSecureStorage} and another {@link OAuthRefreshListener}.
*/
class OAuthHelperListener implements OAuthRefreshListener {
    private final IAuthSecureStorage authSecureStorage;
    private final OAuthRefreshListener configListener;

    private String refreshToken;

    public OAuthHelperListener(IAuthSecureStorage authSecureStorage, OAuthRefreshListener configListener) {
        this.authSecureStorage = authSecureStorage;
        this.configListener = configListener;

        if (authSecureStorage != null && authSecureStorage.getAuth() != null) {
            this.refreshToken = authSecureStorage.getAuth().getRefreshToken();
        }
    }

    @Override
    public void onRefresh(IAuthData newAuthData) {

        // look for refresh token update or revocation
        if (authSecureStorage != null
            && (newAuthData == null || !newAuthData.getRefreshToken().equals(refreshToken))) {
            authSecureStorage.saveAuth(newAuthData);
        }
        if (configListener != null) {
            configListener.onRefresh(newAuthData);
        }

        // update cached refresh token
        refreshToken = newAuthData != null ? newAuthData.getRefreshToken() : null;
    }
}
