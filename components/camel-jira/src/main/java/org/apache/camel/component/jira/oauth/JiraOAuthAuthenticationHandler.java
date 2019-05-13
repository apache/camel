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
package org.apache.camel.component.jira.oauth;

import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;

import com.atlassian.httpclient.api.Request;
import com.atlassian.jira.rest.client.api.AuthenticationHandler;
import com.atlassian.jira.rest.client.api.RestClientException;
import com.google.api.client.auth.oauth.OAuthParameters;
import com.google.api.client.auth.oauth.OAuthRsaSigner;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.apache.ApacheHttpTransport;
import com.google.api.client.repackaged.org.apache.commons.codec.binary.Base64;

/**
 * This authentication handler uses the 3-legged OAuth approach described in https://developer.atlassian.com/server/jira/platform/oauth/
 * The user must manually retrieve a verification code, access token and consumer key to use this authenticator.
 */
public class JiraOAuthAuthenticationHandler implements AuthenticationHandler {

    private OAuthParameters parameters;

    public JiraOAuthAuthenticationHandler(String consumerKey, String verificationCode, String privateKey, String accessToken,
            String jiraUrl) {
        String accessTokenUrl = jiraUrl + "/plugins/servlet/oauth/access-token";
        JiraOAuthGetAccessToken jiraAccessToken = new JiraOAuthGetAccessToken(accessTokenUrl);
        jiraAccessToken.consumerKey = consumerKey;
        try {
            jiraAccessToken.signer = getOAuthRsaSigner(privateKey);
        } catch (Exception e) {
            throw new RestClientException("Error generating the OAuth Authorization RSA Signer", e);
        }
        jiraAccessToken.transport = new ApacheHttpTransport();
        jiraAccessToken.verifier = verificationCode;
        jiraAccessToken.temporaryToken = accessToken;
        parameters = jiraAccessToken.createParameters();
    }

    @Override
    public void configure(Request.Builder builder) {
        try {
            OAuthHttpClientDecorator.OAuthAuthenticatedRequestBuilder oauthBuilder =
                    (OAuthHttpClientDecorator.OAuthAuthenticatedRequestBuilder) builder;
            parameters.computeNonce();
            parameters.computeTimestamp();
            parameters.computeSignature(oauthBuilder.method.name(), new GenericUrl(oauthBuilder.getUri()));
            builder.setHeader("Authorization", parameters.getAuthorizationHeader());
        } catch (Exception e) {
            throw new RestClientException("Error generating the OAuth Authorization request parameter", e);
        }
    }

    private OAuthRsaSigner getOAuthRsaSigner(String privateKey) throws NoSuchAlgorithmException, InvalidKeySpecException {
        byte[] privateBytes = Base64.decodeBase64(privateKey);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(privateBytes);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        OAuthRsaSigner oAuthRsaSigner = new OAuthRsaSigner();
        oAuthRsaSigner.privateKey = kf.generatePrivate(keySpec);
        return oAuthRsaSigner;
    }
}
