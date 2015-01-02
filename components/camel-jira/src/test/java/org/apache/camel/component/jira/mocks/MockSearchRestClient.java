/**
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
package org.apache.camel.component.jira.mocks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import com.atlassian.jira.rest.client.NullProgressMonitor;
import com.atlassian.jira.rest.client.ProgressMonitor;
import com.atlassian.jira.rest.client.SearchRestClient;
import com.atlassian.jira.rest.client.domain.BasicIssue;
import com.atlassian.jira.rest.client.domain.Comment;
import com.atlassian.jira.rest.client.domain.FavouriteFilter;
import com.atlassian.jira.rest.client.domain.SearchResult;
import org.joda.time.DateTime;


public class MockSearchRestClient implements SearchRestClient {

    private static final String KEY_BASE = "CAMELJIRA-";
    private final List<BasicIssue> issues = new ArrayList<BasicIssue>();
    private final Map<Long, List<Comment>> comments = new HashMap<Long, List<Comment>>();
    private AtomicLong basicIssueId = new AtomicLong(0);

    @Override
    public SearchResult searchJql(String s, ProgressMonitor progressMonitor) {
        return null;
    }

    @Override
    public SearchResult searchJql(String s, int i, int i2, ProgressMonitor progressMonitor) {
        return null;
    }

    @Override
    public SearchResult searchJqlWithFullIssues(String jql, int maxPerQuery, int start, ProgressMonitor progressMonitor) {
        List<BasicIssue> result = new ArrayList<BasicIssue>();
        for (BasicIssue issue : issues) {
            if (issue.getId() >= start) {
                result.add(issue);
                if (result.size() >= maxPerQuery) {
                    break;
                }
            }
        }
        return new MockSearchResult(start, maxPerQuery, issues.size(), result);
    }

    @Override
    public Iterable<FavouriteFilter> getFavouriteFilters(NullProgressMonitor nullProgressMonitor) {
        return null;
    }

    public BasicIssue addIssue() {
        String key = KEY_BASE + basicIssueId.get();
        BasicIssue basicIssue = new BasicIssue(null, key, basicIssueId.getAndIncrement());
        issues.add(basicIssue);
        return basicIssue;
    }

    public Comment addCommentToIssue(BasicIssue issue, String commentText) {
        // URI self, String body, @Nullable BasicUser author, @Nullable BasicUser updateAuthor, DateTime creationDate, DateTime updateDate, Visibility visibility, @Nullable Long id
        DateTime now = new DateTime();
        Comment comment = new Comment(null, commentText, null, null, now, null, null, issue.getId());
        List<Comment> commentsForIssue = comments.get(issue.getId());
        if (commentsForIssue == null) {
            commentsForIssue = new ArrayList<Comment>();
        }
        commentsForIssue.add(comment);
        comments.put(issue.getId(), commentsForIssue);

        return comment;
    }

    public List<Comment> getCommentsForIssue(Long issueId) {
        return comments.get(issueId);
    }


    public BasicIssue getBasicIssue(String key) {
        BasicIssue emptyResponse = new BasicIssue(null, "", (long) -1);
        for (BasicIssue bi : issues) {
            if (bi.getKey().equals(key)) {
                return bi;
            }
        }
        return emptyResponse;
    }
}


