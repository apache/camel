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
import org.apache.camel.util.ObjectHelper;
import org.kohsuke.github.GHPullRequest;

/**
 * Producer endpoint that closes a pull request. The endpoint requires the "GitHubPullRequest" header, identifying the
 * pull request number (integer).
 */
public class ClosePullRequestProducer extends AbstractGitHub2Producer {

    public ClosePullRequestProducer(GitHub2Endpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        Integer pullRequestNumber = exchange.getIn().getHeader(GitHub2Constants.GITHUB_PULLREQUEST, Integer.class);
        if (ObjectHelper.isEmpty(pullRequestNumber)) {
            throw new IllegalArgumentException("Pull request number must be specified");
        }

        GHPullRequest pullRequest = getRepository().getPullRequest(pullRequestNumber);
        pullRequest.close();

        // copy the header of in message to the out message
        exchange.getMessage().copyFrom(exchange.getIn());
        exchange.getMessage().setBody(pullRequest);
    }
}
