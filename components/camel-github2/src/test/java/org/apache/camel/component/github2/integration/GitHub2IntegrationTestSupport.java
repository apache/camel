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
package org.apache.camel.component.github2.integration;

import org.apache.camel.CamelContext;
import org.apache.camel.component.github2.GitHub2Component;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeAll;

/**
 * Base class for GitHub2 integration tests.
 *
 * To run integration tests: mvn verify -Dgithub2.test.token=ghp_... -Dgithub2.test.repoOwner=owner
 * -Dgithub2.test.repoName=repo
 */
public abstract class GitHub2IntegrationTestSupport extends CamelTestSupport {

    protected static String oauthToken;
    protected static String repoOwner;
    protected static String repoName;

    @BeforeAll
    public static void checkConfiguration() {
        oauthToken = System.getProperty("github2.test.token");
        if (oauthToken == null || oauthToken.isEmpty()) {
            throw new IllegalStateException(
                    "github2.test.token system property is not set. " +
                                            "Run tests with: mvn verify -Dgithub2.test.token=ghp_...");
        }

        repoOwner = System.getProperty("github2.test.repoOwner");
        if (repoOwner == null || repoOwner.isEmpty()) {
            throw new IllegalStateException(
                    "github2.test.repoOwner system property is not set. " +
                                            "Run tests with: mvn verify -Dgithub2.test.repoOwner=owner");
        }

        repoName = System.getProperty("github2.test.repoName");
        if (repoName == null || repoName.isEmpty()) {
            throw new IllegalStateException(
                    "github2.test.repoName system property is not set. " +
                                            "Run tests with: mvn verify -Dgithub2.test.repoName=repo");
        }
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        GitHub2Component component = new GitHub2Component();
        component.setOauthToken(oauthToken);
        context.addComponent("github2", component);
        return context;
    }

    protected String getRepoOwner() {
        return repoOwner;
    }

    protected String getRepoName() {
        return repoName;
    }

    protected String getOauthToken() {
        return oauthToken;
    }
}
