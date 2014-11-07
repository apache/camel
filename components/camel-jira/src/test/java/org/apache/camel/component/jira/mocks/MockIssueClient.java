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

import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.util.Collection;
import java.util.Set;

import com.atlassian.jira.rest.client.GetCreateIssueMetadataOptions;
import com.atlassian.jira.rest.client.IssueRestClient;
import com.atlassian.jira.rest.client.ProgressMonitor;
import com.atlassian.jira.rest.client.domain.Attachment;
import com.atlassian.jira.rest.client.domain.BasicComponent;
import com.atlassian.jira.rest.client.domain.BasicIssue;
import com.atlassian.jira.rest.client.domain.BasicIssueType;
import com.atlassian.jira.rest.client.domain.BasicPriority;
import com.atlassian.jira.rest.client.domain.BasicProject;
import com.atlassian.jira.rest.client.domain.BasicResolution;
import com.atlassian.jira.rest.client.domain.BasicStatus;
import com.atlassian.jira.rest.client.domain.BasicUser;
import com.atlassian.jira.rest.client.domain.BasicVotes;
import com.atlassian.jira.rest.client.domain.BasicWatchers;
import com.atlassian.jira.rest.client.domain.ChangelogGroup;
import com.atlassian.jira.rest.client.domain.CimProject;
import com.atlassian.jira.rest.client.domain.Comment;
import com.atlassian.jira.rest.client.domain.Field;
import com.atlassian.jira.rest.client.domain.Issue;
import com.atlassian.jira.rest.client.domain.IssueLink;
import com.atlassian.jira.rest.client.domain.Subtask;
import com.atlassian.jira.rest.client.domain.TimeTracking;
import com.atlassian.jira.rest.client.domain.Transition;
import com.atlassian.jira.rest.client.domain.Version;
import com.atlassian.jira.rest.client.domain.Votes;
import com.atlassian.jira.rest.client.domain.Watchers;
import com.atlassian.jira.rest.client.domain.Worklog;
import com.atlassian.jira.rest.client.domain.input.AttachmentInput;
import com.atlassian.jira.rest.client.domain.input.FieldInput;
import com.atlassian.jira.rest.client.domain.input.IssueInput;
import com.atlassian.jira.rest.client.domain.input.LinkIssuesInput;
import com.atlassian.jira.rest.client.domain.input.TransitionInput;
import com.atlassian.jira.rest.client.domain.input.WorklogInput;


import org.codehaus.jettison.json.JSONObject;
import org.joda.time.DateTime;

public class MockIssueClient implements IssueRestClient {
    private MockSearchRestClient mockSearchRestClient;

    public MockIssueClient(MockSearchRestClient mockSearchRestClient) {
        this.mockSearchRestClient = mockSearchRestClient;
    }

    @Override
    public BasicIssue createIssue(IssueInput issue, ProgressMonitor progressMonitor) {
        return null;
    }

    @Override
    public Iterable<CimProject> getCreateIssueMetadata(GetCreateIssueMetadataOptions options, ProgressMonitor progressMonitor) {
        return null;
    }

    @Override
    public Issue getIssue(String issueKey, ProgressMonitor progressMonitor) {
        String summary = null;
        URI self = null;
        BasicProject project = null;
        BasicIssueType issueType = null;
        BasicStatus status = null;

        String description = null;
        BasicPriority priority = null;
        BasicResolution resolution = null;
        Collection<Attachment> attachments = null;
        BasicUser reporter = null;
        BasicUser assignee = null;
        DateTime creationDate = null;
        DateTime updateDate = null;
        DateTime dueDate = null;
        Collection<Version> affectedVersions = null;
        Collection<Version> fixVersions = null;
        Collection<BasicComponent> components = null;
        TimeTracking timeTracking = null;
        Collection<Field> fields = null;
        URI transitionsUri = null;
        Collection<IssueLink> issueLinks = null;
        BasicVotes votes = null;
        Collection<Worklog> worklogs = null;
        BasicWatchers watchers = null;
        Iterable<String> expandos = null;
        Collection<Subtask> subtasks = null;
        Collection<ChangelogGroup> changelog = null;
        Set<String> labels = null;
        JSONObject rawObject = null;

        BasicIssue basicIssue = mockSearchRestClient.getBasicIssue(issueKey);
        Collection<Comment> comments = mockSearchRestClient.getCommentsForIssue(basicIssue.getId());
        Issue issue = new Issue(summary, self, basicIssue.getKey(), basicIssue.getId(), project, issueType, status,
                description, priority, resolution, attachments, reporter, assignee, creationDate, updateDate,
                dueDate, affectedVersions, fixVersions, components, timeTracking, fields, comments,
                transitionsUri, issueLinks,
                votes, worklogs, watchers, expandos, subtasks, changelog, labels, rawObject);
        return issue;
    }

    @Override
    public Issue getIssue(String issueKey, Iterable<Expandos> expand, ProgressMonitor progressMonitor) {
        return null;
    }

    @Override
    public Watchers getWatchers(URI watchersUri, ProgressMonitor progressMonitor) {
        return null;
    }

    @Override
    public Votes getVotes(URI votesUri, ProgressMonitor progressMonitor) {
        return null;
    }

    @Override
    public Iterable<Transition> getTransitions(URI transitionsUri, ProgressMonitor progressMonitor) {
        return null;
    }

    @Override
    public Iterable<Transition> getTransitions(Issue issue, ProgressMonitor progressMonitor) {
        return null;
    }

    @Override
    public void transition(URI transitionsUri, TransitionInput transitionInput, ProgressMonitor progressMonitor) {

    }

    @Override
    public void transition(Issue issue, TransitionInput transitionInput, ProgressMonitor progressMonitor) {

    }

    @Override
    public void update(Issue issue, Iterable<FieldInput> fields, ProgressMonitor progressMonitor) {

    }

    @Override
    public void removeIssue(URI issueUri, boolean deleteSubtasks, ProgressMonitor progressMonitor) {

    }

    @Override
    public void removeIssue(BasicIssue issue, boolean deleteSubtasks, ProgressMonitor progressMonitor) {

    }

    @Override
    public void removeIssue(Long issueId, boolean deleteSubtasks, ProgressMonitor progressMonitor) {

    }

    @Override
    public void removeIssue(String issueKey, boolean deleteSubtasks, ProgressMonitor progressMonitor) {

    }

    @Override
    public void vote(URI votesUri, ProgressMonitor progressMonitor) {

    }

    @Override
    public void unvote(URI votesUri, ProgressMonitor progressMonitor) {

    }

    @Override
    public void watch(URI watchersUri, ProgressMonitor progressMonitor) {

    }

    @Override
    public void unwatch(URI watchersUri, ProgressMonitor progressMonitor) {

    }

    @Override
    public void addWatcher(URI watchersUri, String username, ProgressMonitor progressMonitor) {

    }

    @Override
    public void removeWatcher(URI watchersUri, String username, ProgressMonitor progressMonitor) {

    }

    @Override
    public void linkIssue(LinkIssuesInput linkIssuesInput, ProgressMonitor progressMonitor) {

    }

    @Override
    public void addAttachment(ProgressMonitor progressMonitor, URI attachmentsUri, InputStream in, String filename) {

    }

    @Override
    public void addAttachments(ProgressMonitor progressMonitor, URI attachmentsUri, AttachmentInput... attachments) {

    }

    @Override
    public void addAttachments(ProgressMonitor progressMonitor, URI attachmentsUri, File... files) {

    }

    @Override
    public void addComment(ProgressMonitor progressMonitor, URI commentsUri, Comment comment) {

    }

    @Override
    public InputStream getAttachment(ProgressMonitor pm, URI attachmentUri) {
        return null;
    }

    @Override
    public void addWorklog(URI worklogUri, WorklogInput worklogInput, ProgressMonitor progressMonitor) {

    }
}
