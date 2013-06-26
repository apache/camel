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
package org.apache.camel.component.yammer;

import org.scribe.model.OAuthConstants;
import org.scribe.model.OAuthRequest;
import org.scribe.model.Response;
import org.scribe.model.Verb;

public class ScribeApiRequestor implements ApiRequestor {

    String apiUrl;
    String apiAccessToken;   
    
    public ScribeApiRequestor(String apiUrl, String apiAccessToken) {
        this.apiUrl = apiUrl;
        this.apiAccessToken = apiAccessToken;
    }
    
    /* (non-Javadoc)
     * @see org.apache.camel.component.yammer.ApiRequestor#send()
     */
    @Override
    public String send() throws Exception {
        OAuthRequest request = new OAuthRequest(Verb.GET, apiUrl);
        request.addQuerystringParameter(OAuthConstants.ACCESS_TOKEN, apiAccessToken);
        Response response = request.send();
        if (response.isSuccessful()) {                    
            return response.getBody();
        } else {
            throw new Exception(String.format("Failed to poll %s. Got response code %s and body: %s", getApiUrl(), response.getCode(), response.getBody()));
        }
    }
    
    public String getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl;
    }

    public String getApiAccessToken() {
        return apiAccessToken;
    }

    public void setApiAccessToken(String apiAccessToken) {
        this.apiAccessToken = apiAccessToken;
    }
}
