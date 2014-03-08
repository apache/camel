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
 * Interface to Google's OAuth services. 
 */
public interface GAuthService {

    /**
     * Gets an unauthorized request token from Google.
     * 
     * @param oauthParameters value object for providing input data and storing result data
     *                        (unauthorized request token).
     */
    void getUnauthorizedRequestToken(OAuthParameters oauthParameters) throws Exception;

    /**
     * Gets an access token from Google.
     * 
     * @param oauthParameters value object for providing input data (authorized request token)
     *                        and storing result data (access token).
     */
    void getAccessToken(OAuthParameters oauthParameters) throws Exception;
    
}
