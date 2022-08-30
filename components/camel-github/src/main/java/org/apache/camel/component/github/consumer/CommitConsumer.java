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

import java.util.List;
import java.util.Queue;
import java.util.Stack;
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

    private static final int CAPACITY = 100;

    private CommitService commitService;
    private final String branchName;
    private final String startingSha;

    // keep a chunk of the last 100 hashes, so we can filter out duplicates
    private final Queue<String> commitHashes = new LinkedBlockingQueue<>(CAPACITY);
    private volatile String lastSha;

    public CommitConsumer(GitHubEndpoint endpoint, Processor processor, String branchName,
                          String startingSha) throws Exception {
        super(endpoint, processor);
        this.branchName = branchName;
        this.startingSha = startingSha;

        Registry registry = endpoint.getCamelContext().getRegistry();
        Object service = registry.lookupByName(GitHubConstants.GITHUB_COMMIT_SERVICE);
        if (service != null) {
            LOG.debug("Using CommitService found in registry {}", service.getClass().getCanonicalName());
            commitService = (CommitService) service;
        } else {
            commitService = new CommitService();
        }
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();
        initService(commitService);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // ensure we start from clean
        commitHashes.clear();
        lastSha = null;

        if (startingSha.equals("last")) {
            LOG.info("GitHub CommitConsumer: Indexing current commits...");
            List<RepositoryCommit> commits = commitService.getCommits(getRepository(), branchName, null);
            for (RepositoryCommit commit : commits) {
                String sha = commit.getSha();
                // make room when adding new elements
                while (commitHashes.size() > CAPACITY - 1) {
                    commitHashes.remove();
                }
                commitHashes.add(sha);
                if (lastSha == null) {
                    lastSha = sha;
                }
            }
            LOG.info("GitHub CommitConsumer: Starting from last sha: {}", lastSha);
        } else if (!startingSha.equals("beginning")) {
            lastSha = startingSha;
            LOG.info("GitHub CommitConsumer: Starting from sha: {}", lastSha);
        } else {
            LOG.info("GitHub CommitConsumer: Starting from beginning");
        }
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();

        commitHashes.clear();
        lastSha = null;
    }

    @Override
    protected int poll() throws Exception {
        List<RepositoryCommit> commits;
        if (lastSha != null) {
            commits = commitService.getCommits(getRepository(), lastSha, null);
        } else {
            commits = commitService.getCommits(getRepository());
        }

        // In the end, we want tags oldest to newest.
        Stack<RepositoryCommit> newCommits = new Stack<>();
        for (RepositoryCommit commit : commits) {
            if (!commitHashes.contains(commit.getSha())) {
                newCommits.push(commit);
                commitHashes.add(commit.getSha());
                lastSha = commit.getSha();
            }
        }

        while (!newCommits.empty()) {
            RepositoryCommit newCommit = newCommits.pop();
            Exchange e = createExchange(true);
            e.getMessage().setHeader(GitHubConstants.GITHUB_COMMIT_AUTHOR, newCommit.getAuthor().getName());
            e.getMessage().setHeader(GitHubConstants.GITHUB_COMMIT_COMMITTER, newCommit.getCommitter().getName());
            e.getMessage().setHeader(GitHubConstants.GITHUB_COMMIT_SHA, newCommit.getSha());
            e.getMessage().setHeader(GitHubConstants.GITHUB_COMMIT_URL, newCommit.getUrl());
            e.getMessage().setBody(newCommit.getCommit().getMessage());
            getProcessor().process(e);
        }
        return newCommits.size();
    }
}
