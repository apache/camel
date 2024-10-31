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
package org.apache.camel.component.github.consumer;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.github.GitHubConstants;
import org.apache.camel.component.github.GitHubEndpoint;
import org.apache.camel.spi.Registry;
import org.eclipse.egit.github.core.RepositoryCommit;
import org.eclipse.egit.github.core.service.CommitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CommitConsumer extends AbstractGitHubConsumer {
    private static final transient Logger LOG = LoggerFactory.getLogger(CommitConsumer.class);

    private static final int CAPACITY = 1000; // in case there is a lot of commits and this runs not very frequently

    private CommitService commitService;
    private final String branchName;
    private final String startingSha;

    // keep a chunk of the last 100 hashes, so we can filter out duplicates
    private final Queue<String> commitHashes = new LinkedBlockingQueue<>(CAPACITY);
    private volatile String lastSha;
    private boolean started = false;

    public CommitConsumer(GitHubEndpoint endpoint, Processor processor, String branchName,
                          String startingSha) throws Exception {
        super(endpoint, processor);
        this.branchName = branchName;
        this.startingSha = startingSha;
    }

    @Override
    public GitHubEndpoint getEndpoint() {
        return (GitHubEndpoint) super.getEndpoint();
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        Registry registry = getEndpoint().getCamelContext().getRegistry();
        Object service = registry.lookupByName(GitHubConstants.GITHUB_COMMIT_SERVICE);
        if (service != null) {
            LOG.debug("Using CommitService found in registry {}", service.getClass().getCanonicalName());
            commitService = (CommitService) service;
        } else {
            commitService = new CommitService();
        }

        initService(commitService);
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
                LOG.info("Indexing current commits on: {}/{}@{}", getEndpoint().getRepoOwner(), getEndpoint().getRepoName(),
                        branchName);
                List<RepositoryCommit> commits = commitService.getCommits(getRepository(), branchName, null);
                if (!commits.isEmpty()) {
                    lastSha = commits.get(0).getSha();
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

            List<RepositoryCommit> commits = commitService.getCommits(getRepository(), branchName, null);

            // clip the list after the last sha
            if (lastSha != null) {
                int pos = -1;
                for (int i = 0; i < commits.size(); i++) {
                    RepositoryCommit commit = commits.get(i);
                    if (lastSha.equals(commit.getSha())) {
                        pos = i;
                        break;
                    }
                }
                if (pos != -1) {
                    commits = commits.subList(0, pos);
                }
            }

            // In the end, we want tags oldest to newest.
            ArrayDeque<RepositoryCommit> newCommits = new ArrayDeque<>();
            for (RepositoryCommit commit : commits) {
                String sha = commit.getSha();
                if (!commitHashes.contains(sha)) {
                    newCommits.push(commit);
                    // make room when adding new elements
                    while (commitHashes.size() > CAPACITY - 1) {
                        commitHashes.remove();
                    }
                    commitHashes.add(sha);
                }
            }

            int counter = 0;
            while (!newCommits.isEmpty()) {
                RepositoryCommit newCommit = newCommits.pop();
                lastSha = newCommit.getSha();
                Exchange e = createExchange(true);
                if (newCommit.getAuthor() != null) {
                    e.getMessage().setHeader(GitHubConstants.GITHUB_COMMIT_AUTHOR, newCommit.getAuthor().getName());
                }
                if (newCommit.getCommitter() != null) {
                    e.getMessage().setHeader(GitHubConstants.GITHUB_COMMIT_COMMITTER, newCommit.getCommitter().getName());
                }
                e.getMessage().setHeader(GitHubConstants.GITHUB_COMMIT_SHA, newCommit.getSha());
                e.getMessage().setHeader(GitHubConstants.GITHUB_COMMIT_URL, newCommit.getUrl());
                e.getMessage().setBody(newCommit.getCommit().getMessage());
                getProcessor().process(e);
                counter++;
            }
            LOG.debug("Last sha: {}", lastSha);
            return counter;
        } finally {
            lock.unlock();
        }
    }

}
