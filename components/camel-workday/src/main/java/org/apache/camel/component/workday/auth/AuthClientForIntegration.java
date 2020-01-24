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

package org.apache.camel.component.workday.auth;

import org.apache.camel.component.workday.WorkdayConfiguration;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpMethodBase;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.PostMethod;

import java.io.IOException;
import java.util.Base64;

public class AuthClientForIntegration implements AutheticationClient {

    private static final String GRANT_TYPE = "grant_type";

    private static final String REFRESH_TOKEN = "refresh_token";

    private static final String CONTENT_TYPE_HEADER = "Content-Type";

    private static final String AUTHORIZATION_HEADER = "Authorization";

    private static final String ACCESS_TOKEN = "access_token";

    private static final String CONTENT_TYPE = "application/x-www-form-urlencoded";

    public static final String BASE_TOKEN_ENDPOINT = "https://%s/ccx/oauth2/%s/token";

    private WorkdayConfiguration workdayConfiguration;

    public AuthClientForIntegration(WorkdayConfiguration workdayConfiguration) {
        this.workdayConfiguration = workdayConfiguration;
    }

    @Override
    public void configure(HttpClient httpClient, HttpMethodBase method) {

        String bearerToken = getBearerToken(httpClient);
        method.addRequestHeader(AUTHORIZATION_HEADER, "Bearer " + bearerToken);

    }

    protected String getBearerToken(HttpClient httpClient) {

        String tokenUrl = String.format(BASE_TOKEN_ENDPOINT,
                workdayConfiguration.getHost(),
                workdayConfiguration.getTenant());

        PostMethod postMethod = createPostMethod(tokenUrl);

        try {
            int statusCode = httpClient.executeMethod(postMethod);

            if (statusCode != HttpStatus.SC_OK) {
                throw new IllegalStateException("Got the invalid http status value '" + postMethod.getStatusLine() + "' as the result of the Token Request '" + tokenUrl + "'");
            }

            String response = postMethod.getResponseBodyAsString();
            return parseResponse(response);

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            postMethod.releaseConnection();
        }

        return null;
    }

    private PostMethod createPostMethod(String tokenUrl) {

        PostMethod postMethod = new PostMethod(tokenUrl);
        postMethod.addParameter(GRANT_TYPE, REFRESH_TOKEN);
        postMethod.addParameter(REFRESH_TOKEN, workdayConfiguration.getTokenRefresh());

        postMethod.addRequestHeader(CONTENT_TYPE_HEADER, CONTENT_TYPE);
        postMethod.addRequestHeader(AUTHORIZATION_HEADER, "Basic " +
                new String(Base64.getEncoder().encode((workdayConfiguration.getClientId() + ":" + workdayConfiguration.getClientSecret()).getBytes())));

        return postMethod;
    }

    private String parseResponse(String response) {

        int tokenIdx = response.indexOf(ACCESS_TOKEN);

        if(tokenIdx < 1)
            throw new IllegalStateException("No valid access token response.");

        response = response.substring(response.indexOf(ACCESS_TOKEN) + 16,  response.length() - 3);

        return response;
    }

}
