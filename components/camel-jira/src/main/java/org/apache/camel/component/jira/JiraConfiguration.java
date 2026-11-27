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
package org.apache.camel.component.jira;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@UriParams
public class JiraConfiguration implements Cloneable {

    @UriParam(label = "security", security = "secret",
              description = "The OAuth 1.0a verification code (oauth_verifier) that Jira showed when the user authorized the"
                            + " request token, for OAuth authentication with the consumerKey, privateKey and accessToken"
                            + " options. Not used when the username option is set.")
    private String verificationCode;

    @UriParam(label = "security", security = "secret",
              description = "The consumer key of the OAuth 1.0a application link in Jira, for OAuth authentication with the"
                            + " privateKey, verificationCode and accessToken options. Not used when the username option is"
                            + " set.")
    private String consumerKey;

    @UriParam(label = "security", security = "secret",
              description = "The RSA private key of the OAuth 1.0a consumer, as Base64-encoded PKCS#8 without the PEM header"
                            + " and footer. The component signs each request with it (RSA-SHA1), for OAuth authentication."
                            + " Not used when the username option is set.")
    private String privateKey;

    @UriParam(label = "security", security = "secret",
              description = "The access token. If none of the consumerKey, privateKey and verificationCode options is set,"
                            + " the component sends it as a personal access token in a Bearer Authorization header."
                            + " Otherwise it is the OAuth 1.0a access token. Not used when the username option is set.")
    private String accessToken;

    @UriParam(description = "The URL of the Jira server, for example https://jira.example.com, to which the component sends"
                            + " its REST API requests.")
    @Metadata(required = true)
    private String jiraUrl;

    @UriParam(label = "security", security = "secret",
              description = "The username for basic authentication, which is the email address of the account on Jira"
                            + " Cloud. If set, the component uses basic authentication with the password option, and does not"
                            + " use the accessToken and OAuth options.")
    private String username;

    @UriParam(label = "security", security = "secret",
              description = "The password for basic authentication, or an API token on Jira Cloud. Only used when the"
                            + " username option is set.")
    private String password;

    @UriParam(defaultValue = "6000",
              description = "The time between two polls of the consumer, in milliseconds unless the timeUnit option sets"
                            + " another unit. The producer does not use it.")
    private Integer delay = 6000;

    public String getVerificationCode() {
        return verificationCode;
    }

    public void setVerificationCode(String verificationCode) {
        this.verificationCode = verificationCode;
    }

    public String getConsumerKey() {
        return consumerKey;
    }

    public void setConsumerKey(String consumerKey) {
        this.consumerKey = consumerKey;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(String privateKey) {
        this.privateKey = privateKey;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getJiraUrl() {
        return jiraUrl;
    }

    public void setJiraUrl(String jiraUrl) {
        this.jiraUrl = jiraUrl;
    }

    public Integer getDelay() {
        return delay;
    }

    public void setDelay(Integer delay) {
        this.delay = delay;
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

    public JiraConfiguration copy() {
        try {
            return (JiraConfiguration) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
