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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ScribeApiRequestor implements ApiRequestor {

    private static final Logger LOG = LoggerFactory.getLogger(ScribeApiRequestor.class);
    String apiUrl;
    String apiAccessToken;   
    
    public ScribeApiRequestor(String apiUrl, String apiAccessToken) {
        this.apiUrl = apiUrl;
        this.apiAccessToken = apiAccessToken;
    }
    
    private String send(Verb verb, String params) throws Exception {
        String url = apiUrl + ((params != null) ? params : "");
        
        OAuthRequest request = new OAuthRequest(verb, url);
        request.addQuerystringParameter(OAuthConstants.ACCESS_TOKEN, apiAccessToken);
        
        // For more details on the “Bearer” token refer to http://tools.ietf.org/html/draft-ietf-oauth-v2-bearer-23
        StringBuilder sb = new StringBuilder();
        sb.append("Bearer ");
        sb.append(apiAccessToken);
        request.addHeader("Authorization",  sb.toString());

        if (LOG.isDebugEnabled()) {
            LOG.debug("Yammer request url: {}", request.getCompleteUrl());
        }
        
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

    @Override
    public String get() throws Exception {
        return send(Verb.GET, null);
    }

    @Override
    public String post(String params) throws Exception {
        return send(Verb.POST, params);
    }
}
