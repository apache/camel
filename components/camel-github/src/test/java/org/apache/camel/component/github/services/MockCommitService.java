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
package org.apache.camel.component.github.services;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.eclipse.egit.github.core.CommitStatus;
import org.eclipse.egit.github.core.IRepositoryIdProvider;
import org.eclipse.egit.github.core.RepositoryCommit;
import org.eclipse.egit.github.core.User;
import org.eclipse.egit.github.core.service.CommitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MockCommitService extends CommitService {
    protected static final Logger LOG = LoggerFactory.getLogger(MockCommitService.class);

    private List<RepositoryCommit> commitsList = new ArrayList<>();
    private AtomicLong fakeSha = new AtomicLong(System.currentTimeMillis());
    private Map<String, CommitStatus> commitStatus = new HashMap<>();

    public synchronized RepositoryCommit addRepositoryCommit() {
        User author = new User();
        author.setEmail("someguy@gmail.com");       // TODO change
        author.setHtmlUrl("http://github/someguy");
        author.setLogin("someguy");

        RepositoryCommit rc = new RepositoryCommit();
        rc.setAuthor(author);
        rc.setSha(fakeSha.incrementAndGet() + "");
        LOG.debug("In MockCommitService added commit with sha " + rc.getSha());
        commitsList.add(rc);

        return rc;
    }

    @Override
    public synchronized List<RepositoryCommit> getCommits(IRepositoryIdProvider repository, String sha, String path) throws IOException {
        LOG.debug("Returning list of size " + commitsList.size());
        return commitsList;
    }

    @Override
    public CommitStatus createStatus(IRepositoryIdProvider repository,
            String sha, CommitStatus status) throws IOException {
        commitStatus.put(sha, status);

        return status;
    }

    public String getNextSha() {
        return fakeSha.incrementAndGet() + "";
    }

    public CommitStatus getCommitStatus(String sha) {
        return commitStatus.get(sha);
    }
}
