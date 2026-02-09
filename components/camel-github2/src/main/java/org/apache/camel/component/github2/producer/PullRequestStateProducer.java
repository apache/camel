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
package org.apache.camel.component.github2.producer;

import org.apache.camel.Exchange;
import org.apache.camel.component.github2.GitHub2Constants;
import org.apache.camel.component.github2.GitHub2Endpoint;
import org.kohsuke.github.GHCommitState;
import org.kohsuke.github.GHCommitStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Producer endpoint that sets the commit status.
 *
 * The endpoint requires the "GitHubPullRequestHeadCommitSHA" header, identifying the commit SHA on which the state will
 * be recorded.
 */
public class PullRequestStateProducer extends AbstractGitHub2Producer {

    private static final Logger LOG = LoggerFactory.getLogger(PullRequestStateProducer.class);

    private final String state;
    private final String targetUrl;

    public PullRequestStateProducer(GitHub2Endpoint endpoint) {
        super(endpoint);
        this.state = endpoint.getState();
        this.targetUrl = endpoint.getTargetUrl();
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        String sha = exchange.getIn().getHeader(GitHub2Constants.GITHUB_PULLREQUEST_HEAD_COMMIT_SHA, String.class);
        String description = exchange.getIn().getBody(String.class);

        GHCommitState ghState = null;
        if (state != null) {
            ghState = GHCommitState.valueOf(state.toUpperCase());
        }

        GHCommitStatus status = getRepository().createCommitStatus(
                sha,
                ghState,
                targetUrl,
                description,
                null // context
        );

        // copy the header of in message to the out message
        exchange.getMessage().copyFrom(exchange.getIn());
        exchange.getMessage().setBody(status);
    }
}
