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
package org.apache.camel.component.git.consumer;

import java.util.Queue;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Processor;
import org.apache.camel.component.RepositoryFactory;
import org.apache.camel.component.git.GitEndpoint;
import org.apache.camel.support.ScheduledBatchPollingConsumer;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractGitConsumer extends ScheduledBatchPollingConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractGitConsumer.class);

    private final GitEndpoint endpoint;
    private Repository repo;
    private Git git;

    protected AbstractGitConsumer(GitEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        this.repo = getLocalRepository();
        this.git = new Git(repo);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        repo.close();
        git.close();
    }

    private Repository getLocalRepository() {
        return RepositoryFactory.of(endpoint);
    }

    public Repository getRepository() {
        return repo;
    }

    protected Git getGit() {
        return git;
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
            Object value = onPreProcessed(exchange);
            getProcessor().process(exchange);
            onProcessed(exchange, value);
        }

        return answer;
    }

    public abstract Object onPreProcessed(Exchange exchange);

    public abstract void onProcessed(Exchange exchange, Object value);

}
