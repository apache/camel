/**
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

import java.util.Calendar;

import org.apache.camel.Exchange;
import org.apache.camel.component.github.GitHubConstants;
import org.apache.camel.component.github.GitHubEndpoint;
import org.apache.camel.spi.Registry;
import org.eclipse.egit.github.core.PullRequest;
import org.eclipse.egit.github.core.service.PullRequestService;

/**
 * Producer endpoint that closes a pull request.  The endpoint requires the "GitHubPullRequest" header,
 * identifying the pull request number (integer).
 */
public class ClosePullRequestProducer extends AbstractGitHubProducer {
    
    private PullRequestService pullRequestService;

    public ClosePullRequestProducer(GitHubEndpoint endpoint) throws Exception {
        super(endpoint);
        
        Registry registry = endpoint.getCamelContext().getRegistry();
        Object service = registry.lookupByName(GitHubConstants.GITHUB_PULL_REQUEST_SERVICE);
        if (service != null) {
            pullRequestService = (PullRequestService) service;
        } else {
            pullRequestService = new PullRequestService();
        }
        initService(pullRequestService);
    }

    public void process(Exchange exchange) throws Exception {
        Integer pullRequestNumber = exchange.getIn().getHeader(GitHubConstants.GITHUB_PULLREQUEST, Integer.class);
        PullRequest pullRequest = pullRequestService.getPullRequest(getRepository(), pullRequestNumber);
        pullRequest.setState("closed");
        pullRequest.setClosedAt(Calendar.getInstance().getTime());
        pullRequest = pullRequestService.editPullRequest(getRepository(), pullRequest);
        
        // support InOut
        if (exchange.getPattern().isOutCapable()) {
            // copy the header of in message to the out message
            exchange.getOut().copyFrom(exchange.getIn());
            exchange.getOut().setBody(pullRequest);
        }
    }

}
