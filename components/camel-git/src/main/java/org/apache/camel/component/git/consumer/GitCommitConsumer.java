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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.git.GitConstants;
import org.apache.camel.component.git.GitEndpoint;
import org.apache.camel.util.ObjectHelper;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;

public class GitCommitConsumer extends AbstractGitConsumer {

    private final List<ObjectId> commitsConsumed = new ArrayList<>();

    public GitCommitConsumer(GitEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    @Override
    public GitEndpoint getEndpoint() {
        return (GitEndpoint) super.getEndpoint();
    }

    @Override
    protected int poll() throws Exception {
        Queue<Object> exchanges = new ArrayDeque<>();

        String branch = getEndpoint().getBranchName();
        Iterable<RevCommit> commits;
        ObjectId id = null;
        if (ObjectHelper.isNotEmpty(branch)) {
            id = getGit().getRepository().resolve(branch);
        }
        if (id != null) {
            commits = getGit().log().add(id).call();
        } else {
            commits = getGit().log().all().call();
        }
        for (RevCommit commit : commits) {
            if (!commitsConsumed.contains(commit.getId())) {
                Exchange e = createExchange(true);
                e.getMessage().setBody(commit.getFullMessage());
                e.getMessage().setHeader(GitConstants.GIT_COMMIT_ID, commit.getId());
                e.getMessage().setHeader(GitConstants.GIT_COMMIT_AUTHOR_NAME, commit.getAuthorIdent().getName());
                e.getMessage().setHeader(GitConstants.GIT_COMMIT_COMMITTER_NAME, commit.getCommitterIdent().getName());
                e.getMessage().setHeader(GitConstants.GIT_COMMIT_TIME, commit.getCommitTime());
                getProcessor().process(e);
                exchanges.add(e);
            }
        }
        return processBatch(exchanges);
    }

    @Override
    public Object onPreProcessed(Exchange exchange) {
        return exchange.getMessage().getHeader(GitConstants.GIT_COMMIT_ID);
    }

    @Override
    public void onProcessed(Exchange exchange, Object value) {
        if (value instanceof ObjectId oid) {
            commitsConsumed.add(oid);
        }
    }
}
