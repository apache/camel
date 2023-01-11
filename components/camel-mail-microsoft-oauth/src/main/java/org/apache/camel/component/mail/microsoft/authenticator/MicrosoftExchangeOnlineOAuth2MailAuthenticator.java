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
package org.apache.camel.component.mail.microsoft.authenticator;

import java.net.MalformedURLException;
import java.util.Collections;
import java.util.Set;

import jakarta.mail.PasswordAuthentication;

import com.microsoft.aad.msal4j.ClientCredentialFactory;
import com.microsoft.aad.msal4j.ClientCredentialParameters;
import com.microsoft.aad.msal4j.ConfidentialClientApplication;
import com.microsoft.aad.msal4j.IAuthenticationResult;
import com.microsoft.aad.msal4j.IClientCredential;
import com.microsoft.aad.msal4j.IConfidentialClientApplication;
import org.apache.camel.component.mail.MailAuthenticator;

/**
 * This Mail Authenticator is intended for users of Microsoft Exchange Online that use an Azure Active Directory
 * instance as Identity Provider and OAuth2 Client Credential Flow as Authentication protocol.
 */
public class MicrosoftExchangeOnlineOAuth2MailAuthenticator extends MailAuthenticator {

    /**
     * The default scope application is requesting access to.
     */
    private static final Set<String> DEFAULT_SCOPES = Collections.singleton("https://outlook.office365.com/.default");

    /**
     * The authenticating authority base URL.
     */
    private static final String AUTHORITY_BASE_URL = "https://login.microsoftonline.com/";

    /**
     * Client ID (Application ID) of the application as registered in the application registration portal
     * (portal.azure.com)
     */
    private String clientId;

    /**
     * secret of application requesting a token
     */
    private String clientSecret;

    /**
     * The authenticating authority or security token service (STS) from which will acquire security tokens.
     */
    private String authority;

    /**
     * The username for login.
     */
    private final String user;

    /**
     * Indicates whether the request should skip looking into the token cache. Be default it is set to false.
     */
    private Boolean skipCache;

    /**
     * The scopes application is requesting access to.
     */
    private Set<String> scopes;

    /**
     * Object containing parameters for client credential flow.
     */
    private final ClientCredentialParameters clientCredentialParameters;

    /**
     * Object used to acquire tokens for confidential client applications (Web Apps, Web APIs, and daemon applications.
     * Confidential client applications are trusted to safely store application secrets, and therefore can be used to
     * acquire tokens in then name of either the application or an user. For details see
     * https://aka.ms/msal4jclientapplications
     */
    private IConfidentialClientApplication confidentialClientApplication;

    public MicrosoftExchangeOnlineOAuth2MailAuthenticator(String tenantId, String clientId, String clientSecret, String user) {
        this(tenantId, clientId, clientSecret, user, false, null, null);
    }

    public MicrosoftExchangeOnlineOAuth2MailAuthenticator(String tenantId, String clientId, String clientSecret, String user,
                                                          Boolean skipCache) {
        this(tenantId, clientId, clientSecret, user, skipCache, null, null);
    }

    public MicrosoftExchangeOnlineOAuth2MailAuthenticator(String tenantId, String clientId, String clientSecret, String user,
                                                          Boolean skipCache, Set<String> scopes) {
        this(tenantId, clientId, clientSecret, user, skipCache, scopes, null);
    }

    public MicrosoftExchangeOnlineOAuth2MailAuthenticator(String tenantId, String clientId, String clientSecret, String user,
                                                          ClientCredentialParameters clientCredentialParameters) {
        this(tenantId, clientId, clientSecret, user, null, null, clientCredentialParameters);
    }

    public MicrosoftExchangeOnlineOAuth2MailAuthenticator(String user,
                                                          IConfidentialClientApplication confidentialClientApplication,
                                                          ClientCredentialParameters clientCredentialParameters) {
        this.user = user;
        this.confidentialClientApplication = confidentialClientApplication;
        this.clientCredentialParameters = clientCredentialParameters;
    }

    private MicrosoftExchangeOnlineOAuth2MailAuthenticator(String tenantId, String clientId, String clientSecret, String user,
                                                           Boolean skipCache, Set<String> scopes,
                                                           ClientCredentialParameters parametes) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.authority = AUTHORITY_BASE_URL + tenantId;
        this.skipCache = skipCache;
        this.user = user;
        this.clientCredentialParameters = parametes;

        if (scopes == null || scopes.isEmpty()) {
            this.scopes = DEFAULT_SCOPES;
        } else {
            this.scopes = scopes;
        }
    }

    @Override
    public PasswordAuthentication getPasswordAuthentication() {
        IAuthenticationResult result = getClientCredential().acquireToken(getClientCredentialParameters()).join();
        return new PasswordAuthentication(user, result.accessToken());
    }

    private IConfidentialClientApplication getClientCredential() {

        if (confidentialClientApplication != null) {
            return confidentialClientApplication;
        }

        try {
            // This is the secret that is created in the Azure portal when registering the application
            IClientCredential credential = ClientCredentialFactory.createFromSecret(clientSecret);

            confidentialClientApplication = ConfidentialClientApplication
                    .builder(clientId, credential)
                    .authority(authority)
                    .build();
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }

        return confidentialClientApplication;
    }

    private ClientCredentialParameters getClientCredentialParameters() {
        if (clientCredentialParameters != null) {
            return clientCredentialParameters;
        }

        // Client credential requests will by default try to look for a valid token in the
        // in-memory token cache. If found, it will return this token. If a token is not found, or the
        // token is not valid, it will fall back to acquiring a token from the AAD service. Although
        // not recommended unless there is a reason for doing so, you can skip the cache lookup
        // by setting skipCache to true.
        return ClientCredentialParameters
                .builder(scopes)
                .skipCache(skipCache)
                .build();
    }
}
