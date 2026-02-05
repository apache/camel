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

import org.apache.camel.component.github2.GitHubClientFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitHub;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for GitHubClientFactory.
 *
 * To run this test: mvn verify -Dgithub2.test.token=ghp_... -Dgithub2.test.repoOwner=owner -Dgithub2.test.repoName=repo
 */
@EnabledIfSystemProperty(named = "github2.test.token", matches = ".+")
public class GitHub2ClientFactoryIT {

    private static String oauthToken;
    private static String repoOwner;
    private static String repoName;

    @BeforeAll
    public static void checkConfiguration() {
        oauthToken = System.getProperty("github2.test.token");
        repoOwner = System.getProperty("github2.test.repoOwner");
        repoName = System.getProperty("github2.test.repoName");
    }

    @Test
    public void testCreateClientWithToken() throws Exception {
        GitHub github = GitHubClientFactory.createClient(oauthToken, null);

        assertNotNull(github);
        assertTrue(github.isCredentialValid());
        assertFalse(github.isAnonymous());
    }

    @Test
    public void testGetCurrentUser() throws Exception {
        GitHub github = GitHubClientFactory.createClient(oauthToken, null);

        GHUser myself = github.getMyself();
        assertNotNull(myself);
        assertNotNull(myself.getLogin());
    }

    @Test
    public void testGetRepository() throws Exception {
        GitHub github = GitHubClientFactory.createClient(oauthToken, null);

        GHRepository repo = github.getRepository(repoOwner + "/" + repoName);
        assertNotNull(repo);
        assertNotNull(repo.getName());
        assertNotNull(repo.getOwnerName());
    }

    @Test
    public void testListCommits() throws Exception {
        GitHub github = GitHubClientFactory.createClient(oauthToken, null);

        GHRepository repo = github.getRepository(repoOwner + "/" + repoName);
        assertNotNull(repo);

        // Just verify we can list commits without error
        var commits = repo.listCommits();
        assertNotNull(commits);
        // Get at least one commit
        var iterator = commits.iterator();
        assertTrue(iterator.hasNext(), "Repository should have at least one commit");
        assertNotNull(iterator.next().getSHA1());
    }
}
