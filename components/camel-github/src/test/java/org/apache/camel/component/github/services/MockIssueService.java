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
import java.util.List;

import org.eclipse.egit.github.core.Comment;
import org.eclipse.egit.github.core.IRepositoryIdProvider;
import org.eclipse.egit.github.core.Issue;
import org.eclipse.egit.github.core.service.IssueService;

public class MockIssueService extends IssueService {

    private List<Comment> comments = new ArrayList<>();
    private MockPullRequestService mockPullRequestService;

    public MockIssueService(MockPullRequestService mockPullRequestService) {
        this.mockPullRequestService = mockPullRequestService;

    }

    @Override
    public List<Comment> getComments(IRepositoryIdProvider repository, int issueNumber) {
        return comments;
    }

    @Override
    public Comment createComment(IRepositoryIdProvider repository, int issueNumber, String commentText) throws IOException {
        Comment addedComment = mockPullRequestService.addComment((long) issueNumber, commentText);
        return addedComment;
    }
    
    @Override
    public Issue createIssue(IRepositoryIdProvider repository, Issue issue) {
        Issue finalIssue = new Issue();
        issue.setBody("There's an error");
        issue.setTitle("Error");
        issue.setId(1L);
        return finalIssue;
    }
    
    @Override
    public Issue getIssue(IRepositoryIdProvider repository, String issueNumber) {
        Issue issue = new Issue();
        issue.setBody("There's an error");
        issue.setTitle("Error");
        issue.setId(1L);
        return issue;
    }
}
