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

import com.google.common.base.Joiner;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class JiraComponentConfigurationTest extends CamelTestSupport {

    static final String USERNAME = "username";
    private static final String USERNAME_VALUE = "claudio";
    private static final String PASSWORD = "password";
    private static final String PASSWORD_VALUE = "myPassword";
    private static final String VERIF_CODE = "verificationCode";
    private static final String VERIF_CODE_VALUE = "My_verification_code_test";
    private static final String CONS_KEY = "consumerKey";
    private static final String CONS_KEY_VALUE = "my_consumer_key_test";
    private static final String ACCESS_TOKEN = "accessToken";
    private static final String ACCESS_TOKEN_VALUE = "my_access_token_test";
    private static final String PRIV_KEY = "privateKey";
    private static final String PRIV_KEY_VALUE = "my_privateKey_test";
    private static final String JIRA_URL = "jiraUrl";
    private static final String JIRA_URL_VALUE = "http://my_jira_server:8080";
    private static final String WATCHED_FIELDS = "watchedFields";
    private static final String WATCHED_FIELDS_VALUE = "Assignee,Components,Priority";
    private static final String SEND_ONLY_CHANGED_FIELD = "sendOnlyUpdatedField";
    private static final boolean SEND_ONLY_CHANGED_FIELD_VALUE = false;

    @Test
    public void createEndpointWithBasicAuthentication() throws Exception {
        JiraComponent component = new JiraComponent(context);
        component.start();
        String query = Joiner.on("&").join(
                concat(JIRA_URL, JIRA_URL_VALUE),
                concat(USERNAME, USERNAME_VALUE),
                concat(PASSWORD, PASSWORD_VALUE));
        JiraEndpoint endpoint = (JiraEndpoint) component.createEndpoint("jira://newIssues?" + query);

        assertEquals("newissues", endpoint.getType().name().toLowerCase());
        assertEquals(JIRA_URL_VALUE, endpoint.getConfiguration().getJiraUrl());
        assertEquals(USERNAME_VALUE, endpoint.getConfiguration().getUsername());
        assertEquals(PASSWORD_VALUE, endpoint.getConfiguration().getPassword());
    }

    @Test
    public void createEndpointWithOAuthentication() throws Exception {
        JiraComponent component = new JiraComponent(context);
        component.start();
        String query = Joiner.on("&").join(
                concat(JIRA_URL, JIRA_URL_VALUE),
                concat(VERIF_CODE, VERIF_CODE_VALUE),
                concat(ACCESS_TOKEN, ACCESS_TOKEN_VALUE),
                concat(CONS_KEY, CONS_KEY_VALUE),
                concat(PRIV_KEY, PRIV_KEY_VALUE));
        JiraEndpoint endpoint = (JiraEndpoint) component.createEndpoint("jira://newComments?" + query);

        assertEquals("newcomments", endpoint.getType().name().toLowerCase());
        assertEquals(JIRA_URL_VALUE, endpoint.getConfiguration().getJiraUrl());
        assertEquals(VERIF_CODE_VALUE, endpoint.getConfiguration().getVerificationCode());
        assertEquals(ACCESS_TOKEN_VALUE, endpoint.getConfiguration().getAccessToken());
        assertEquals(CONS_KEY_VALUE, endpoint.getConfiguration().getConsumerKey());
        assertEquals(PRIV_KEY_VALUE, endpoint.getConfiguration().getPrivateKey());
    }

    @Test
    public void createEndpointWithPersonalAccessTokenAuthentication() throws Exception {
        JiraComponent component = new JiraComponent(context);
        component.start();
        String query = Joiner.on("&").join(
                concat(JIRA_URL, JIRA_URL_VALUE),
                concat(ACCESS_TOKEN, ACCESS_TOKEN_VALUE));
        JiraEndpoint endpoint = (JiraEndpoint) component.createEndpoint("jira://updateIssue?" + query);

        assertEquals("updateissue", endpoint.getType().name().toLowerCase());
        assertEquals(JIRA_URL_VALUE, endpoint.getConfiguration().getJiraUrl());
        assertNull(endpoint.getConfiguration().getVerificationCode());
        assertEquals(ACCESS_TOKEN_VALUE, endpoint.getConfiguration().getAccessToken());
        assertNull(endpoint.getConfiguration().getConsumerKey());
        assertNull(endpoint.getConfiguration().getPrivateKey());
    }

    @Test
    public void createWatchChangesEndpoint() throws Exception {
        JiraComponent component = new JiraComponent(context);
        component.start();
        String query = Joiner.on("&").join(
                concat(JIRA_URL, JIRA_URL_VALUE),
                concat(USERNAME, USERNAME_VALUE),
                concat(PASSWORD, PASSWORD_VALUE),
                concat(SEND_ONLY_CHANGED_FIELD, SEND_ONLY_CHANGED_FIELD_VALUE + ""),
                concat(WATCHED_FIELDS, WATCHED_FIELDS_VALUE));
        JiraEndpoint endpoint = (JiraEndpoint) component.createEndpoint("jira://watchUpdates?" + query);

        assertEquals("watchupdates", endpoint.getType().name().toLowerCase());
        assertEquals(WATCHED_FIELDS_VALUE, endpoint.getWatchedFields());
        assertEquals(SEND_ONLY_CHANGED_FIELD_VALUE, endpoint.isSendOnlyUpdatedField());
    }

    private String concat(String key, String val) {
        return key + "=" + val;
    }
}
