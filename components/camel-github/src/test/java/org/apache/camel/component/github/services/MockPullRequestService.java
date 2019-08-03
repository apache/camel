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
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.egit.github.core.CommitComment;
import org.eclipse.egit.github.core.CommitFile;
import org.eclipse.egit.github.core.IRepositoryIdProvider;
import org.eclipse.egit.github.core.PullRequest;
import org.eclipse.egit.github.core.User;
import org.eclipse.egit.github.core.service.PullRequestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MockPullRequestService extends PullRequestService {
    protected static final Logger LOG = LoggerFactory.getLogger(MockPullRequestService.class);

    private Map<Long, PullRequest> pullRequests = new HashMap<>();
    private List<CommitComment> emptyComments = new ArrayList<>();
    private AtomicInteger pullRequestNumber = new AtomicInteger(101);
    private AtomicInteger commentId = new AtomicInteger(500);
    private Map<Long, List<CommitComment>> allComments = new HashMap<>();
    private Map<Integer, List<CommitFile>> files = new HashMap<>();

    @Override
    public List<CommitComment> getComments(IRepositoryIdProvider repository, int pullRequestId) {
        Long id = new Long(pullRequestId);
        if (allComments.containsKey(id)) {
            List<CommitComment> comments = allComments.get(id);
            return comments;
        } else {
            return emptyComments;
        }
    }

    private User createAuthor() {
        User author = new User();
        author.setEmail("someguy@gmail.com");
        author.setHtmlUrl("http://github/someguy");
        author.setLogin("someguy");

        return author;
    }

    public CommitComment addComment(Long pullRequestId, String bodyText) {
        CommitComment commitComment = new CommitComment();

        User author = createAuthor();
        commitComment.setUser(author);
        commitComment.setCommitId("" + pullRequestId);
        commitComment.setId(commentId.getAndIncrement());
        commitComment.setBody(bodyText);
        commitComment.setBodyText(bodyText);

        List<CommitComment> comments;
        if (allComments.containsKey(pullRequestId)) {
            comments = allComments.get(pullRequestId);
        } else {
            comments = new ArrayList<>();
        }
        comments.add(commitComment);
        allComments.put(pullRequestId, comments);

        return commitComment;
    }

    public PullRequest addPullRequest(String title) {
        User author = createAuthor();

        PullRequest pullRequest = new PullRequest();
        pullRequest.setUser(author);
        pullRequest.setHtmlUrl("https://github.com/someguy/somerepo/pull" + pullRequestNumber);
        pullRequest.setTitle(title);
        pullRequest.setNumber(pullRequestNumber.get());
        pullRequest.setId(pullRequestNumber.get());
        pullRequest.setState("open");
        pullRequests.put(pullRequest.getId(), pullRequest);

        pullRequestNumber.incrementAndGet();
        return pullRequest;
    }

    @Override
    public PullRequest getPullRequest(IRepositoryIdProvider repository, int id) throws IOException {
        PullRequest pullRequest = pullRequests.get((long) id);
        return pullRequest;
    }

    @Override
    public PullRequest editPullRequest(IRepositoryIdProvider repository, PullRequest request) throws IOException {
        pullRequests.put(request.getId(), request);
        return request;
    }

    @Override
    public synchronized List<PullRequest> getPullRequests(IRepositoryIdProvider repository, String state) {
        List<PullRequest> result = new ArrayList<>();

        for (Long id : pullRequests.keySet()) {
            PullRequest pr = pullRequests.get(id);
            if (pr.getState().equals(state)) {
                result.add(pr);
            }
        }

        LOG.debug("Returning list of " + result.size() + " pull requests with state " + state);
        return result;
    }

    public void setFiles(int id, List<CommitFile> commitFiles) {
        files.put(id, commitFiles);
    }

    @Override
    public List<CommitFile> getFiles(IRepositoryIdProvider repository, int id) throws IOException {
        return files.get(id);
    }
}
