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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.apache.camel.component.workday.WorkdayConfiguration;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicNameValuePair;

public class AuthClientForIntegration implements AutheticationClient {

    public static final String BASE_TOKEN_ENDPOINT = "https://%s/ccx/oauth2/%s/token";

    private static final String GRANT_TYPE = "grant_type";

    private static final String REFRESH_TOKEN = "refresh_token";

    private static final String CONTENT_TYPE_HEADER = "Content-Type";

    private static final String AUTHORIZATION_HEADER = "Authorization";

    private static final String ACCESS_TOKEN = "access_token";

    private static final String CONTENT_TYPE = "application/x-www-form-urlencoded";

    private WorkdayConfiguration workdayConfiguration;

    public AuthClientForIntegration(WorkdayConfiguration workdayConfiguration) {
        this.workdayConfiguration = workdayConfiguration;
    }

    @Override
    public void configure(CloseableHttpClient httpClient, HttpRequestBase method) throws IOException {

        String bearerToken = getBearerToken(httpClient);
        method.addHeader(AUTHORIZATION_HEADER, "Bearer " + bearerToken);

    }

    protected String getBearerToken(CloseableHttpClient httpClient) throws IOException {

        String tokenUrl = String.format(BASE_TOKEN_ENDPOINT, workdayConfiguration.getHost(), workdayConfiguration.getTenant());

        HttpPost httpPost = createPostMethod(tokenUrl);

        CloseableHttpResponse httpResponse = httpClient.execute(httpPost);

        if (httpResponse.getStatusLine().getStatusCode() != HttpStatus.SC_OK) {
            throw new IllegalStateException("Got the invalid http status value '" + httpResponse.getStatusLine() + "' as the result of the Token Request '" + tokenUrl + "'");
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        httpResponse.getEntity().writeTo(baos);
        return parseResponse(baos.toString());
    }

    private HttpPost createPostMethod(String tokenUrl) throws UnsupportedEncodingException {

        List<NameValuePair> nvps = new ArrayList<NameValuePair>();
        nvps.add(new BasicNameValuePair(GRANT_TYPE, REFRESH_TOKEN));
        nvps.add(new BasicNameValuePair(REFRESH_TOKEN, workdayConfiguration.getTokenRefresh()));

        HttpPost postMethod = new HttpPost(tokenUrl);
        postMethod.addHeader(CONTENT_TYPE_HEADER, CONTENT_TYPE);
        postMethod.addHeader(AUTHORIZATION_HEADER,
                             "Basic " + new String(Base64.getEncoder().encode((workdayConfiguration.getClientId() + ":" + workdayConfiguration.getClientSecret()).getBytes())));
        postMethod.setEntity(new UrlEncodedFormEntity(nvps, "UTF-8"));

        return postMethod;
    }

    private String parseResponse(String response) {

        int tokenIdx = response.indexOf(ACCESS_TOKEN);

        if (tokenIdx < 1) {
            throw new IllegalStateException("No valid access token response.");
        }

        response = response.substring(response.indexOf(ACCESS_TOKEN) + 16, response.length() - 3);

        return response;
    }

}
