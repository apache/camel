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
package org.apache.camel.component.cyberark.vault;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.cyberark.vault.client.ConjurClient;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

/**
 * Configuration for CyberArk Conjur Vault component
 */
@UriParams
public class CyberArkVaultConfiguration implements Cloneable {

    @UriPath(description = "The default ID of the secret, which is the ID of a Conjur variable, for example db/password. The"
                           + " producer uses it when the secretId option is not set, and the CamelCyberArkVaultSecretId header"
                           + " replaces both.")
    @Metadata(required = true)
    private String label;

    @UriParam(description = "The ID of the secret, which is the ID of a Conjur variable, for example db/password. If not set on"
                            + " the endpoint, the value of the URI path is used. The CamelCyberArkVaultSecretId header replaces"
                            + " this option.")
    private String secretId;

    @UriParam(description = "The operation that the producer performs. getSecret reads the value of the secret into the message"
                            + " body. createSecret stores the message body, or the CamelCyberArkVaultSecretValue header, as a new"
                            + " value of a variable that is declared in Conjur policy. The CamelCyberArkVaultOperation header"
                            + " replaces this option.",
              defaultValue = "getSecret")
    private CyberArkVaultOperations operation = CyberArkVaultOperations.getSecret;

    @UriParam(description = "The base URL of the Conjur server, for example https://conjur.example.com. The component appends"
                            + " the paths of the Conjur REST API, such as /authn and /secrets, to it. Not used when the"
                            + " conjurClient option is set.")
    @Metadata(required = true)
    private String url;

    @UriParam(description = "The Conjur organization account, which is part of the path of every request to the Conjur REST"
                            + " API. Not used when the conjurClient option is set.")
    @Metadata(required = true)
    private String account;

    @UriParam(description = "The login of the Conjur user or host to authenticate as, for example admin or host/myapp. It is"
                            + " used together with the apiKey option or the password option.",
              label = "security", security = "secret")
    private String username;

    @UriParam(description = "The password of the Conjur user, used with the username option when neither authToken nor"
                            + " apiKey is set. The component sends it in the Conjur authenticate request, in place of an API"
                            + " key.",
              label = "security", security = "secret")
    private String password;

    @UriParam(description = "The API key of the Conjur user or host given by the username option. The component exchanges it"
                            + " for an access token, and again when Conjur rejects the token. Used when the authToken option is"
                            + " not set.",
              label = "security", security = "secret")
    private String apiKey;

    @UriParam(description = "A Conjur access token, as returned by the Conjur authenticate request, which the component sends"
                            + " Base64-encoded in the Authorization header. It takes precedence over apiKey and password. The"
                            + " component cannot renew it, so the requests fail once the token has expired.",
              label = "security", security = "secret")
    private String authToken;

    @UriParam(label = "security", defaultValue = "true", security = "insecure:ssl", insecureValue = "false")
    private boolean verifySsl = true;

    @UriParam(label = "security")
    private String certificatePath;

    @UriParam(description = "To use a custom ConjurClient, for example #myClient from the registry. When set, the url, account,"
                            + " credential and SSL options are not used, and the endpoint does not close the client.")
    private ConjurClient conjurClient;

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getSecretId() {
        return secretId;
    }

    public void setSecretId(String secretId) {
        this.secretId = secretId;
    }

    public CyberArkVaultOperations getOperation() {
        return operation;
    }

    public void setOperation(CyberArkVaultOperations operation) {
        this.operation = operation;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getAccount() {
        return account;
    }

    public void setAccount(String account) {
        this.account = account;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getAuthToken() {
        return authToken;
    }

    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }

    public boolean isVerifySsl() {
        return verifySsl;
    }

    /**
     * Whether to verify the SSL certificate and the host name of the CyberArk Conjur server. Setting it to false trusts
     * any server, so use it only for testing.
     */
    public void setVerifySsl(boolean verifySsl) {
        this.verifySsl = verifySsl;
    }

    public String getCertificatePath() {
        return certificatePath;
    }

    /**
     * Path of a file with the SSL certificate to trust when connecting to CyberArk Conjur, for example the certificate
     * of a private CA, in PEM or DER format. The certificates in the file are trusted instead of the default trust
     * store of the JVM. A path of the file system, or a resource with the file: or classpath: prefix, for example
     * classpath:conjur-ca.pem. Other prefixes, such as http:, are not supported. Not used when verifySsl is false.
     */
    public void setCertificatePath(String certificatePath) {
        this.certificatePath = certificatePath;
    }

    public ConjurClient getConjurClient() {
        return conjurClient;
    }

    public void setConjurClient(ConjurClient conjurClient) {
        this.conjurClient = conjurClient;
    }

    // *************************************************
    //
    // *************************************************

    public CyberArkVaultConfiguration copy() {
        try {
            return (CyberArkVaultConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
