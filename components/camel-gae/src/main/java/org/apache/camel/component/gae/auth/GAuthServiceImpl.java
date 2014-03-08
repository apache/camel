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
package org.apache.camel.component.gae.auth;

import com.google.gdata.client.authn.oauth.OAuthParameters;

/**
 * Implements the remote interaction to Google's OAuth services.
 */
class GAuthServiceImpl implements GAuthService {

    private GAuthEndpoint endpoint;

    public GAuthServiceImpl(GAuthEndpoint endpoint) {
        this.endpoint = endpoint;
    }

    public void getUnauthorizedRequestToken(OAuthParameters oauthParameters) throws Exception {
        endpoint.newOAuthHelper().getUnauthorizedRequestToken(oauthParameters);
    }

    public void getAccessToken(OAuthParameters oauthParameters) throws Exception {
        endpoint.newOAuthHelper().getAccessToken(oauthParameters);
    }

}
