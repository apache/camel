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
package org.apache.camel.component.jira;

import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import javax.annotation.Nullable;

import com.atlassian.jira.rest.client.api.domain.Attachment;
import com.atlassian.jira.rest.client.api.domain.BasicComponent;
import com.atlassian.jira.rest.client.api.domain.BasicPriority;
import com.atlassian.jira.rest.client.api.domain.BasicWatchers;
import com.atlassian.jira.rest.client.api.domain.Comment;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.IssueLink;
import com.atlassian.jira.rest.client.api.domain.IssueLinkType;
import com.atlassian.jira.rest.client.api.domain.IssueType;
import com.atlassian.jira.rest.client.api.domain.Resolution;
import com.atlassian.jira.rest.client.api.domain.Status;
import com.atlassian.jira.rest.client.api.domain.User;
import com.atlassian.jira.rest.client.api.domain.Worklog;
import com.google.common.collect.ImmutableMap;
import org.apache.commons.lang3.StringUtils;
import org.joda.time.DateTime;

import static com.atlassian.jira.rest.client.api.domain.User.S48_48;
import static org.apache.camel.component.jira.JiraTestConstants.KEY;
import static org.apache.camel.component.jira.JiraTestConstants.TEST_JIRA_URL;

public final class Utils {

    public static User userAssignee;
    static {
        try {
            userAssignee = new User(null, "user-test", "User Test", "user@test", true, null, buildUserAvatarUris("user-test", 10082L), null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private static IssueType issueType = new IssueType(null, 1L, "Bug", false, "Bug", null);

    private Utils() {
    }

    public static Issue createIssue(long id) {
        return createIssueWithComments(id, 0);
    }

    public static Issue createIssue(long id, String summary, String key, IssueType issueType, String description,
            BasicPriority priority, User assignee, Collection<BasicComponent> components, BasicWatchers watchers) {
        URI selfUri = URI.create(TEST_JIRA_URL + "/rest/api/latest/issue/" + id);
        return new Issue(summary, selfUri, KEY + "-" + id, id, null, issueType, null, description, priority, null, null, null,
                assignee, null, null, null, null, null, components, null, null, null, null, null, null, null, watchers,
                null, null, null, null, null);
    }

    public static Issue transitionIssueDone(Issue issue, Status status, Resolution resolution) {
        return new Issue(issue.getSummary(), issue.getSelf(), issue.getKey(), issue.getId(), null, issue.getIssueType(),
                status, issue.getDescription(), issue.getPriority(), resolution, null, null,
                issue.getAssignee(), null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null);
    }

    public static Issue createIssueWithAttachment(long id, String summary, String key, IssueType issueType, String description,
            BasicPriority priority, User assignee, Collection<Attachment> attachments) {
        URI selfUri = URI.create(TEST_JIRA_URL + "/rest/api/latest/issue/" + id);
        return new Issue(summary, selfUri, KEY + "-" + id, id, null, issueType, null, description, priority, null, attachments, null,
                assignee, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null);
    }

    public static Issue createIssueWithComments(long id, int numComments) {
        Collection<Comment> comments = new ArrayList<>();
        if (numComments > 0) {
            for (int idx = 1; idx < numComments + 1; idx++) {
                Comment c = newComment(id, idx, "A test comment " + idx + " for " + KEY + "-" + id);
                comments.add(c);
            }
        }
        return createIssueWithComments(id, comments);
    }

    public static Issue createIssueWithComments(long id, Collection<Comment> comments) {
        URI selfUri = URI.create(TEST_JIRA_URL + "/rest/api/latest/issue/" + id);
        return new Issue("jira summary test " + id, selfUri, KEY + "-" + id, id, null, issueType, null, "Description " + id,
                null, null, null, null, userAssignee, null, null, null, null, null, null, null, null, comments, null, null,
                null, null, null, null, null, null, null, null);
    }

    public static Issue createIssueWithLinks(long id, Collection<IssueLink> issueLinks) {
        URI selfUri = URI.create(TEST_JIRA_URL + "/rest/api/latest/issue/" + id);
        return new Issue("jira summary test " + id, selfUri, KEY + "-" + id, id, null, issueType, null, "Description " + id,
            null, null, null, null, userAssignee, null, null, null, null, null, null, null, null, null, null, issueLinks,
            null, null, null, null, null, null, null, null);
    }

    public static Issue createIssueWithWorkLogs(long id, Collection<Worklog> worklogs) {
        URI selfUri = URI.create(TEST_JIRA_URL + "/rest/api/latest/issue/" + id);
        return new Issue("jira summary test " + id, selfUri, KEY + "-" + id, id, null, issueType, null, "Description " + id,
            null, null, null, null, userAssignee, null, null, null, null, null, null, null, null, null, null, null,
            null, worklogs, null, null, null, null, null, null);
    }

    public static Comment newComment(long issueId, int newCommentId, String comment) {
        DateTime now = DateTime.now();
        long id = Long.parseLong(issueId + "0" + newCommentId);
        URI selfUri = URI.create(TEST_JIRA_URL + "/rest/api/latest/issue/" + issueId + "/comment");
        return new Comment(selfUri, comment, null, null, now, null, null, id);
    }

    public static IssueLink newIssueLink(long issueId, int newLinkId, String comment) {
        long id = Long.parseLong(issueId + "0" + newLinkId);
        URI issueUri = URI.create(TEST_JIRA_URL + "/rest/api/latest/issue/" + id);
        IssueLinkType relatesTo = new IssueLinkType("Relates", "relates to", IssueLinkType.Direction.OUTBOUND);

        return new IssueLink(KEY, issueUri, relatesTo);
    }

    public static Worklog newWorkLog(long issueId, Integer minutesSpent, String comment) {
        DateTime now = DateTime.now();
        URI issueUri = URI.create(TEST_JIRA_URL + "/rest/api/latest/issue/" + issueId);
        URI selfUri = URI.create(TEST_JIRA_URL + "/rest/api/latest/issue/" + issueId + "/comment");

        return new Worklog(selfUri, issueUri, null, null, comment, now, null, null, minutesSpent, null);
    }

    private static Map<String, URI> buildUserAvatarUris(@Nullable String user, Long avatarId) throws Exception {
        final ImmutableMap.Builder<String, URI> builder = ImmutableMap.builder();
        builder.put(S48_48, buildUserAvatarUri(user, avatarId));
        return builder.build();
    }

    private static URI buildUserAvatarUri(@Nullable String userName, Long avatarId) throws Exception {
        // secure/useravatar?size=small&ownerId=admin&avatarId=10054
        final StringBuilder sb = new StringBuilder("secure/useravatar?");

        // Optional user name
        if (StringUtils.isNotBlank(userName)) {
            sb.append("ownerId=").append(userName).append("&");
        }

        // avatar Id
        sb.append("avatarId=").append(avatarId);
        String relativeAvatarUrl = sb.toString();
        URI avatarUrl = new URL(TEST_JIRA_URL + "/" + relativeAvatarUrl).toURI();
        return avatarUrl;
    }

}
