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
package org.apache.camel.component.github.producer;

import java.util.List;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.github.GitHubComponent;
import org.apache.camel.component.github.GitHubComponentTestBase;
import org.apache.camel.component.github.GitHubConstants;
import org.eclipse.egit.github.core.PullRequest;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClosePullRequestProducerTest extends GitHubComponentTestBase {
    public static final String PULL_REQUEST_PRODUCER_ENDPOINT = "direct:validPullRequest";
    protected static final Logger LOG = LoggerFactory.getLogger(ClosePullRequestProducerTest.class);
    private long latestPullRequestId;
    

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {

            @Override
            public void configure() throws Exception {
                context.addComponent("github", new GitHubComponent());
                from(PULL_REQUEST_PRODUCER_ENDPOINT)
                        .process(new ClosePullRequestProducerProcessor())
                        .to("github://closePullRequest?username=someguy&password=apassword&repoOwner=anotherguy&repoName=somerepo");
            } // end of configure


        };
    }


    @Test
    public void testPullRequestCommentProducer() throws Exception {
        // Create a pull request
        PullRequest pullRequest = pullRequestService.addPullRequest("testPullRequestCommentProducer");
        latestPullRequestId = pullRequest.getId();


        // Close it
        Endpoint closePullRequestEndpoint = getMandatoryEndpoint(PULL_REQUEST_PRODUCER_ENDPOINT);
        Exchange exchange = closePullRequestEndpoint.createExchange();
        template.send(closePullRequestEndpoint, exchange);

        Thread.sleep(1 * 1000);

        // Verify that it was closed

        List<PullRequest> closedPullRequests = pullRequestService.getPullRequests(null, "closed");
        assertNotNull(closedPullRequests);
        boolean found = false;
        for (PullRequest pr : closedPullRequests) {
            if (pr.getId() == latestPullRequestId) {
                found = true;
                break;
            }
        }

        assertTrue("Didn't find pull request " + latestPullRequestId, found);
    }


    public class ClosePullRequestProducerProcessor implements Processor {
        @Override
        public void process(Exchange exchange) throws Exception {
            Message in = exchange.getIn();
            Map<String, Object> headers = in.getHeaders();
            headers.put(GitHubConstants.GITHUB_PULLREQUEST, latestPullRequestId);
        }
    }


}
