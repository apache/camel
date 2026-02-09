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
package org.apache.camel.component.github2;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class GitHub2ComponentTest {

    @Test
    public void testComponentConfiguration() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            GitHub2Component component = new GitHub2Component();
            component.setCamelContext(context);
            component.setOauthToken("test-token");
            component.setApiUrl("https://github.mycompany.com/api/v3");

            assertEquals("test-token", component.getOauthToken());
            assertEquals("https://github.mycompany.com/api/v3", component.getApiUrl());
        }
    }

    @Test
    public void testEndpointConfiguration() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            GitHub2Component component = new GitHub2Component();
            component.setCamelContext(context);
            component.setOauthToken("test-token");
            context.addComponent("github2", component);
            context.start();

            GitHub2Endpoint endpoint = (GitHub2Endpoint) context.getEndpoint(
                    "github2:commit/main?repoOwner=apache&repoName=camel&startingSha=last");

            assertNotNull(endpoint);
            assertEquals(GitHub2Type.COMMIT, endpoint.getType());
            assertEquals("main", endpoint.getBranchName());
            assertEquals("apache", endpoint.getRepoOwner());
            assertEquals("camel", endpoint.getRepoName());
            assertEquals("last", endpoint.getStartingSha());
        }
    }

    @Test
    public void testEndpointWithPullRequest() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            GitHub2Component component = new GitHub2Component();
            component.setCamelContext(context);
            component.setOauthToken("test-token");
            context.addComponent("github2", component);
            context.start();

            GitHub2Endpoint endpoint = (GitHub2Endpoint) context.getEndpoint(
                    "github2:pullRequest?repoOwner=apache&repoName=camel");

            assertNotNull(endpoint);
            assertEquals(GitHub2Type.PULLREQUEST, endpoint.getType());
            assertEquals("apache", endpoint.getRepoOwner());
            assertEquals("camel", endpoint.getRepoName());
        }
    }

    @Test
    public void testEndpointWithApiUrl() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            GitHub2Component component = new GitHub2Component();
            component.setCamelContext(context);
            component.setOauthToken("test-token");
            context.addComponent("github2", component);
            context.start();

            GitHub2Endpoint endpoint = (GitHub2Endpoint) context.getEndpoint(
                    "github2:commit/main?repoOwner=myorg&repoName=myrepo&apiUrl=https://github.mycompany.com/api/v3");

            assertNotNull(endpoint);
            assertEquals("https://github.mycompany.com/api/v3", endpoint.getApiUrl());
        }
    }

    @Test
    public void testConstants() {
        // Verify constants are accessible
        assertNotNull(GitHub2Constants.GITHUB_PULLREQUEST);
        assertNotNull(GitHub2Constants.GITHUB_COMMIT_SHA);
        assertNotNull(GitHub2Constants.GITHUB_COMMIT_AUTHOR);
    }

    @Test
    public void testTypeEnum() {
        assertEquals("commit", GitHub2Type.COMMIT.name().toLowerCase());
        assertEquals("pullrequest", GitHub2Type.PULLREQUEST.name().toLowerCase());
        assertEquals("tag", GitHub2Type.TAG.name().toLowerCase());
        assertEquals("closepullrequest", GitHub2Type.CLOSEPULLREQUEST.name().toLowerCase());
        assertEquals("pullrequestcomment", GitHub2Type.PULLREQUESTCOMMENT.name().toLowerCase());
        assertEquals("pullrequestfiles", GitHub2Type.PULLREQUESTFILES.name().toLowerCase());
        assertEquals("pullrequeststate", GitHub2Type.PULLREQUESTSTATE.name().toLowerCase());
        assertEquals("createissue", GitHub2Type.CREATEISSUE.name().toLowerCase());
        assertEquals("getcommitfile", GitHub2Type.GETCOMMITFILE.name().toLowerCase());
        assertEquals("event", GitHub2Type.EVENT.name().toLowerCase());
    }
}
