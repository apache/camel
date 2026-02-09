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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Processor;
import org.apache.camel.component.github2.GitHub2Constants;
import org.apache.camel.component.github2.GitHub2Endpoint;
import org.kohsuke.github.GHCommit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CommitConsumer extends AbstractGitHub2Consumer {

    private static final Logger LOG = LoggerFactory.getLogger(CommitConsumer.class);
    private static final int CAPACITY = 1000;

    private final String branchName;
    private final String startingSha;

    private final Queue<String> commitHashes = new LinkedBlockingQueue<>(CAPACITY);
    private volatile String lastSha;
    private boolean started = false;

    public CommitConsumer(GitHub2Endpoint endpoint, Processor processor, String branchName,
                          String startingSha) {
        super(endpoint, processor);
        this.branchName = branchName;
        this.startingSha = startingSha;
    }

    @Override
    protected void doStart() throws Exception {
        lock.lock();
        try {
            super.doStart();

            // ensure we start from clean
            commitHashes.clear();
            lastSha = null;

            if (startingSha.equals("last")) {
                LOG.info("Indexing current commits on: {}/{}@{}", getEndpoint().getRepoOwner(),
                        getEndpoint().getRepoName(), branchName);
                List<GHCommit> commits = new ArrayList<>();
                for (GHCommit commit : getRepository().queryCommits().from(branchName).list().withPageSize(1)) {
                    commits.add(commit);
                    break; // just get the first one
                }
                if (!commits.isEmpty()) {
                    lastSha = commits.get(0).getSHA1();
                }
                LOG.info("Starting from last sha: {}", lastSha);
            } else if (!startingSha.equals("beginning")) {
                lastSha = startingSha;
                LOG.info("Starting from sha: {}", lastSha);
            } else {
                LOG.info("Starting from beginning");
            }
            started = true;
        } finally {
            lock.unlock();
        }
    }

    @Override
    protected void doStop() throws Exception {
        lock.lock();
        try {
            super.doStop();

            commitHashes.clear();
            lastSha = null;
            started = false;
        } finally {
            lock.unlock();
        }
    }

    @Override
    protected int poll() throws Exception {
        lock.lock();
        try {
            if (!started) {
                return 0;
            }

            List<GHCommit> commits = new ArrayList<>();
            for (GHCommit commit : getRepository().queryCommits().from(branchName).list().withPageSize(100)) {
                commits.add(commit);
                // Stop after 100 commits to avoid fetching too many
                if (commits.size() >= 100) {
                    break;
                }
            }

            // clip the list after the last sha
            if (lastSha != null) {
                int pos = -1;
                for (int i = 0; i < commits.size(); i++) {
                    GHCommit commit = commits.get(i);
                    if (lastSha.equals(commit.getSHA1())) {
                        pos = i;
                        break;
                    }
                }
                if (pos != -1) {
                    commits = commits.subList(0, pos);
                }
            }

            // In the end, we want commits oldest to newest
            ArrayDeque<GHCommit> newCommits = new ArrayDeque<>();
            for (GHCommit commit : commits) {
                String sha = commit.getSHA1();
                if (!commitHashes.contains(sha)) {
                    newCommits.push(commit);
                    // make room when adding new elements
                    while (commitHashes.size() > CAPACITY - 1) {
                        commitHashes.remove();
                    }
                    commitHashes.add(sha);
                }
            }

            Queue<Object> exchanges = new ArrayDeque<>();
            while (!newCommits.isEmpty()) {
                GHCommit newCommit = newCommits.pop();
                Exchange e = createExchange(true);
                if (newCommit.getAuthor() != null) {
                    e.getMessage().setHeader(GitHub2Constants.GITHUB_COMMIT_AUTHOR, newCommit.getAuthor().getName());
                }
                if (newCommit.getCommitter() != null) {
                    e.getMessage().setHeader(GitHub2Constants.GITHUB_COMMIT_COMMITTER, newCommit.getCommitter().getName());
                }
                e.getMessage().setHeader(GitHub2Constants.GITHUB_COMMIT_SHA, newCommit.getSHA1());
                e.getMessage().setHeader(GitHub2Constants.GITHUB_COMMIT_URL, newCommit.getHtmlUrl().toString());
                if (getEndpoint().isCommitMessageAsBody()) {
                    e.getMessage().setBody(newCommit.getCommitShortInfo().getMessage());
                } else {
                    e.getMessage().setBody(newCommit);
                }
                exchanges.add(e);
            }
            int counter = processBatch(exchanges);
            LOG.debug("Last sha: {}", lastSha);
            return counter;
        } finally {
            lock.unlock();
        }
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
            this.lastSha = exchange.getMessage().getHeader(GitHub2Constants.GITHUB_COMMIT_SHA, String.class);
            getProcessor().process(exchange);
        }

        return answer;
    }
}
