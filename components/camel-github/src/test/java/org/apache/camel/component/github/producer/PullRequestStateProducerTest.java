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

import java.util.Date;
import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.github.GitHubComponentTestBase;
import org.apache.camel.component.github.GitHubConstants;
import org.eclipse.egit.github.core.CommitStatus;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

public class PullRequestStateProducerTest extends GitHubComponentTestBase {
    private String commitsha;

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            @Override
            public void configure() {
                from("direct:validPullRequest")
                        .process(new MockPullRequestStateProducerProcessor())
                        .to("github://pullRequestState?state=success&repoOwner=anotherguy&repoName=somerepo");
            } // end of configure

        };
    }

    @Test
    public void testPullRequestStateProducer() {
        commitsha = commitService.getNextSha();

        Endpoint stateProducerEndpoint = getMandatoryEndpoint("direct:validPullRequest");
        Exchange exchange = stateProducerEndpoint.createExchange();
        String text = "Message sent at " + new Date();
        exchange.getIn().setBody(text);
        Exchange response = template.send(stateProducerEndpoint, exchange);

        assertNotNull(response.getMessage().getBody());

        if (!(response.getMessage().getBody() instanceof CommitStatus)) {
            fail("Expecting CommitStatus");
        }

        CommitStatus status = response.getMessage().getBody(CommitStatus.class);

        // Check status set on commit service
        if (commitService.getCommitStatus(commitsha) != status) {
            fail("Commit status sent to service is different from response");
        }

        assertEquals("success", status.getState());

        assertEquals(status.getDescription(), text);
    }

    public class MockPullRequestStateProducerProcessor implements Processor {
        @Override
        public void process(Exchange exchange) {
            Message in = exchange.getIn();
            Map<String, Object> headers = in.getHeaders();
            headers.put(GitHubConstants.GITHUB_PULLREQUEST_HEAD_COMMIT_SHA, commitsha);
        }
    }

}
