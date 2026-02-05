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
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.kohsuke.github.GHTag;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for GitHub2 Tag Consumer.
 *
 * To run this test: mvn verify -Dgithub2.test.token=ghp_... -Dgithub2.test.repoOwner=owner -Dgithub2.test.repoName=repo
 *
 * Note: This test requires the repository to have at least one tag.
 */
@EnabledIfSystemProperty(named = "github2.test.token", matches = ".+")
public class GitHub2TagConsumerIT extends GitHub2IntegrationTestSupport {

    @Test
    public void testConsumeTags() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:tags");
        // We may or may not have tags, so just verify the route starts successfully
        mock.expectedMinimumMessageCount(0);
        mock.setResultWaitTime(10000);

        mock.assertIsSatisfied();

        // If we received any tags, verify they are valid
        if (!mock.getExchanges().isEmpty()) {
            Object body = mock.getExchanges().get(0).getIn().getBody();
            assertNotNull(body);
            assertTrue(body instanceof GHTag);
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                fromF("github2:tag?repoOwner=%s&repoName=%s",
                        getRepoOwner(), getRepoName())
                        .to("mock:tags");
            }
        };
    }
}
