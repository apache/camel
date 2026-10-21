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

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.LinkedBlockingQueue;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.github2.GitHub2Constants;
import org.apache.camel.component.github2.GitHub2Endpoint;
import org.kohsuke.github.GHCommit;
import org.kohsuke.github.GHUser;
import org.kohsuke.github.GitUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CommitConsumer extends AbstractGitHub2Consumer {

    private static final Logger LOG = LoggerFactory.getLogger(CommitConsumer.class);
    private static final int CAPACITY = 1000;
    private static final int MAX_COMMITS_WITHOUT_LAST_SHA = 100;

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
            // read up to the last routed commit: the commits pushed since then all come before it. Stopping after the
            // first 100 would lose the older ones when more were pushed between two polls, as lastSha then moves to
            // the newest one. Without a last commit, or when it is not among the remembered number of newest commits
            // (it is no longer on the branch), only the 100 newest are considered
            int limit = lastSha != null ? CAPACITY : MAX_COMMITS_WITHOUT_LAST_SHA;
            boolean lastShaFound = false;
            for (GHCommit commit : getRepository().queryCommits().from(branchName).list().withPageSize(100)) {
                commits.add(commit);
                if (lastSha != null && lastSha.equals(commit.getSHA1())) {
                    lastShaFound = true;
                    break;
                }
                if (commits.size() >= limit) {
                    break;
                }
            }
            if (!lastShaFound && commits.size() > MAX_COMMITS_WITHOUT_LAST_SHA) {
                commits = commits.subList(0, MAX_COMMITS_WITHOUT_LAST_SHA);
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
                }
            }
            if (newCommits.isEmpty() && lastSha != null && !lastShaFound && !commits.isEmpty()) {
                // the last routed commit is no longer on the branch (a force push or a reset to an older commit), and
                // the newest commits were all routed before: continue from the newest one, so that the next polls do
                // not read the remembered number of commits again to look for the last routed one
                LOG.info("Last routed commit {} is no longer on branch {}, continuing from the newest commit {}",
                        lastSha, branchName, commits.get(0).getSHA1());
                lastSha = commits.get(0).getSHA1();
            }

            Queue<Object> exchanges = new ArrayDeque<>();
            while (!newCommits.isEmpty()) {
                GHCommit newCommit = newCommits.pop();
                Exchange e = createExchange(true);
                setUserHeaders(e, newCommit);
                e.getMessage().setHeader(GitHub2Constants.GITHUB_COMMIT_SHA, newCommit.getSHA1());
                e.getMessage().setHeader(GitHub2Constants.GITHUB_COMMIT_URL, newCommit.getHtmlUrl().toString());
                if (getEndpoint().isCommitMessageAsBody()) {
                    GHCommit.ShortInfo info = shortInfo(newCommit);
                    e.getMessage().setBody(info != null ? info.getMessage() : null);
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

    /**
     * Sets the headers with the name of the author and of the committer. {@link GHCommit#getAuthor()} and
     * {@link GHCommit#getCommitter()} are the only calls of a poll that do requests of their own for a commit of the
     * list: one for the full commit and one for each user that is not cached yet. A commit whose lookup fails (the user
     * no longer exists, a server or network error) is routed all the same, as a lookup that always fails would
     * otherwise fail every poll and hold back the commits that follow. The header then has the name that git recorded
     * in the commit, which is part of the list, and is not set when there is none.
     */
    private void setUserHeaders(Exchange exchange, GHCommit commit) {
        IOException failure = null;
        try {
            GHUser author = commit.getAuthor();
            if (author != null) {
                exchange.getMessage().setHeader(GitHub2Constants.GITHUB_COMMIT_AUTHOR, author.getName());
            }
        } catch (IOException e) {
            failure = e;
            setGitUserHeader(exchange, GitHub2Constants.GITHUB_COMMIT_AUTHOR, commit, true);
        }
        try {
            GHUser committer = commit.getCommitter();
            if (committer != null) {
                exchange.getMessage().setHeader(GitHub2Constants.GITHUB_COMMIT_COMMITTER, committer.getName());
            }
        } catch (IOException e) {
            failure = failure != null ? failure : e;
            setGitUserHeader(exchange, GitHub2Constants.GITHUB_COMMIT_COMMITTER, commit, false);
        }
        if (failure != null) {
            // one line for a commit, without the stack trace. The commit is routed and then not read again, so this
            // is not repeated by the following polls
            LOG.warn("Cannot look up the author or the committer of commit {} on GitHub, using the names recorded in the"
                     + " commit instead. Caused by: {}",
                    commit.getSHA1(), failure.toString());
        }
    }

    private static void setGitUserHeader(Exchange exchange, String header, GHCommit commit, boolean author) {
        GHCommit.ShortInfo info = shortInfo(commit);
        if (info != null) {
            GitUser user = author ? info.getAuthor() : info.getCommitter();
            if (user != null && user.getName() != null) {
                exchange.getMessage().setHeader(header, user.getName());
            }
        }
    }

    /**
     * The data of the git commit (message, author and committer as git recorded them). It is part of the list of
     * commits, so this does no request. Returns null in the case that the commit came without it and it cannot be
     * loaded either.
     */
    private static GHCommit.ShortInfo shortInfo(GHCommit commit) {
        try {
            return commit.getCommitShortInfo();
        } catch (IOException e) {
            LOG.debug("Cannot load the data of commit {}: {}", commit.getSHA1(), e.toString());
            return null;
        }
    }

    @Override
    protected void onRouting(Exchange exchange) {
        String sha = exchange.getMessage().getHeader(GitHub2Constants.GITHUB_COMMIT_SHA, String.class);
        // make room when adding new elements
        while (commitHashes.size() > CAPACITY - 1) {
            commitHashes.remove();
        }
        commitHashes.add(sha);
        this.lastSha = sha;
    }
}
