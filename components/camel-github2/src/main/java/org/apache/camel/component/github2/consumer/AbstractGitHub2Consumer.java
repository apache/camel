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
package org.apache.camel.component.github2.consumer;

import java.util.Queue;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Processor;
import org.apache.camel.component.github2.GitHub2Constants;
import org.apache.camel.component.github2.GitHub2Endpoint;
import org.apache.camel.component.github2.GitHubClientFactory;
import org.apache.camel.support.ScheduledBatchPollingConsumer;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractGitHub2Consumer extends ScheduledBatchPollingConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractGitHub2Consumer.class);

    private final GitHub2Endpoint endpoint;
    private GitHub github;
    private GHRepository repository;

    protected AbstractGitHub2Consumer(GitHub2Endpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        // Check registry for existing GitHub client (useful for testing)
        github = endpoint.getCamelContext().getRegistry()
                .lookupByNameAndType(GitHub2Constants.GITHUB_CLIENT, GitHub.class);

        if (github == null) {
            LOG.debug("Creating GitHub client for endpoint");
            github = GitHubClientFactory.createClient(
                    endpoint.getOauthToken(),
                    endpoint.getApiUrl());
        } else {
            LOG.debug("Using GitHub client found in registry");
        }

        repository = github.getRepository(
                endpoint.getRepoOwner() + "/" + endpoint.getRepoName());
    }

    @Override
    public GitHub2Endpoint getEndpoint() {
        return (GitHub2Endpoint) super.getEndpoint();
    }

    protected GitHub getGitHub() {
        return github;
    }

    protected GHRepository getRepository() {
        return repository;
    }

    @Override
    public int processBatch(Queue<Object> exchanges) throws Exception {
        int total = exchanges.size();
        int answer = total;
        if (this.maxMessagesPerPoll > 0 && total > this.maxMessagesPerPoll) {
            LOG.debug("Limiting to maximum messages to poll {} as there were {} messages in this poll.",
                    this.maxMessagesPerPoll, total);
            total = this.maxMessagesPerPoll;
        }

        for (int index = 0; index < total && this.isBatchAllowed(); ++index) {
            Exchange exchange = (Exchange) exchanges.poll();
            exchange.setProperty(ExchangePropertyKey.BATCH_INDEX, index);
            exchange.setProperty(ExchangePropertyKey.BATCH_SIZE, total);
            exchange.setProperty(ExchangePropertyKey.BATCH_COMPLETE, index == total - 1);
            this.pendingExchanges = total - index - 1;
            getProcessor().process(exchange);
        }

        return answer;
    }
}
