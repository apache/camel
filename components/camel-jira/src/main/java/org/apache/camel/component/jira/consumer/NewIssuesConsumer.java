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

package org.apache.camel.component.jira.consumer;

import java.net.URI;
import java.util.Queue;

import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.User;
import org.apache.camel.Processor;
import org.apache.camel.component.jira.JiraEndpoint;
import org.apache.camel.util.CastUtils;
import org.joda.time.DateTimeZone;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Consumes new JIRA issues.
 * <p>
 * To correctly support getting issues across multiple projects, the issues are ordered using created attribute, the
 * creation date is later used in the JQL to get only the new issues. JQL does not support timestamp or seconds and the
 * most useful format is yyyy-MM-dd HH:mm.
 * <p>
 * Using the date and time in JQL is a bit tricky the creationDate of an issue is returned in server timezone, but the
 * date and time in the JQL must match the timezone of the user executing the query to work properly.
 * <p>
 * NOTE: We manually add "ORDER BY created DESC" to the JQL in order to optimize startup (the latest issues one at a
 * time) and to correctly get the newest issue.
 */
public class NewIssuesConsumer extends AbstractJiraConsumer {
    private static final Logger LOG = LoggerFactory.getLogger(NewIssuesConsumer.class);
    // Date format used in the JQL below
    private static final DateTimeFormatter JIRA_DATE_FORMAT = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm");
    // Even the operator is ">", the JQL behaves more like ">=", so it will return all issues created at that minute and
    // later
    private static final String NEW_ISSUES_JQL_FORMAT = "created > \"%s\" AND %s ORDER BY created DESC";

    // Last issue that was processed by the integration
    private Issue latestIssue;
    // timezone of the current user authenticated
    private DateTimeZone userTimeZone;

    public NewIssuesConsumer(JiraEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        latestIssue = findLatestIssue();
        if (latestIssue != null) {
            LOG.debug("Init: Latest issue: {}", latestIssue.getKey());
        }

        userTimeZone = getUserTimeZone();
    }

    /**
     * Change the creation date of the latest issue to the timezone of the user.
     *
     * @return timestamp string used in the JQL
     */
    private String getServerTimestamp() {
        return latestIssue.getCreationDate().withZone(userTimeZone).toString(JIRA_DATE_FORMAT);
    }

    /**
     * Gets the timezone of the currently authenticated user. Basic auth configuration has precedence over OAuth
     * credentials as in the login process.
     *
     * @return {@link DateTimeZone} of the user
     */
    private DateTimeZone getUserTimeZone() {
        URI userURI = URI.create(getEndpoint().getConfiguration().getJiraUrl()).resolve("rest/api/latest/myself");
        final User user =
                getEndpoint().getClient().getUserClient().getUser(userURI).claim();
        final String timezone = user.getTimezone();

        LOG.debug("Using user {} with timezone {}", user.getName(), timezone);
        return DateTimeZone.forID(timezone);
    }

    /**
     * At the start, find the latest issue, so that only newer issues are processed.
     *
     * @return latest {@link Issue} or null
     */
    private Issue findLatestIssue() {
        try {
            // get the issues, ordered so that the latest one is the first one returned by the query
            Queue<Issue> issues = getIssues(getEndpoint().getJql() + " ORDER BY created DESC", 1);
            if (!issues.isEmpty()) {
                return issues.peek();
            }
        } catch (Exception e) {
            // ignore
        }
        // in case there aren't any issues...
        return null;
    }

    protected int doPoll() throws Exception {
        // it may happen the poll() is called while the route is doing the initial load,
        // this way we need to wait for the latestIssueId being associated to the last indexed issue id
        Queue<Issue> newIssues = getNewIssues();
        // In the end, we want only *new* issues oldest to newest. New issues returned are ordered descendant already.
        processBatch(CastUtils.cast(newIssues));
        return newIssues.size();
    }

    /**
     * Get the new issues and save the latest one.
     *
     * @return queue of new issues
     */
    private Queue<Issue> getNewIssues() {
        String jqlFilter;
        // If we have processed an issue before, use its timestamp to get only the newer ones
        if (latestIssue != null) {
            jqlFilter = String.format(
                    NEW_ISSUES_JQL_FORMAT, getServerTimestamp(), getEndpoint().getJql());
        } else {
            jqlFilter = getEndpoint().getJql();
        }

        Queue<Issue> issues = getIssues(jqlFilter);

        if (!issues.isEmpty()) {
            if (latestIssue != null) {
                // remove all issues that are older than the latestIssue (including), because those were processed by
                // previous polls
                issues.removeIf(i -> i.getCreationDate().isBefore(latestIssue.getCreationDate())
                        || i.getCreationDate().isEqual(latestIssue.getCreationDate()));
            }

            // we might filter out all issues in the previous statement, resulting in no new issues
            if (!issues.isEmpty()) {
                // if there are issues left in the queue, save the newest one
                latestIssue = issues.peek();
                LOG.debug("Latest issue: {}", latestIssue.getKey());
            }
        }
        return issues;
    }
}
