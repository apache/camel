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

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.github2.GitHub2Constants;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for GitHub2 Commit Consumer.
 *
 * To run this test: mvn verify -Dgithub2.test.token=ghp_... -Dgithub2.test.repoOwner=owner -Dgithub2.test.repoName=repo
 * -Dgithub2.test.branch=main
 */
@EnabledIfSystemProperty(named = "github2.test.token", matches = ".+")
public class GitHub2CommitConsumerIT extends GitHub2IntegrationTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(GitHub2CommitConsumerIT.class);

    @Test
    public void testConsumeCommits() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:commits");
        // We expect at least one commit from the beginning of the repo
        // Using startingSha=beginning means we should get all commits up to the limit
        mock.expectedMinimumMessageCount(1);
        mock.setResultWaitTime(60000);

        // Wait for the route to be started and first poll to complete
        Thread.sleep(5000);

        mock.assertIsSatisfied();

        // Verify headers are set
        assertTrue(mock.getExchanges().size() > 0, "Should have received at least one commit");
        assertNotNull(mock.getExchanges().get(0).getIn().getHeader(GitHub2Constants.GITHUB_COMMIT_SHA));
        LOG.info("Received {} commits", mock.getExchanges().size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                String branch = System.getProperty("github2.test.branch", "main");
                LOG.info("Testing with branch: {}, owner: {}, repo: {}", branch, getRepoOwner(), getRepoName());
                // Consume commits from the beginning to ensure we get some data
                // Use a short initial delay and polling delay
                fromF("github2:commit/%s?repoOwner=%s&repoName=%s&startingSha=beginning&initialDelay=0&delay=2000",
                        branch, getRepoOwner(), getRepoName())
                        .log("Received commit: ${header.CamelGitHubCommitSha}")
                        .to("mock:commits");
            }
        };
    }
}
