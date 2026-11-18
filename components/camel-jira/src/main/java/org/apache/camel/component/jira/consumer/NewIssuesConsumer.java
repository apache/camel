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
import org.joda.time.DateTime;
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
 * <p>
 * If the search for the latest issue fails at startup, the polls ask for the issues created since the consumer started
 * instead, until one is found: the JQL asks for the minute of the start, and only the issues whose creation date is
 * after the start are kept. That start is read from the local clock and compared with the creation dates of the server,
 * so while the lookup has failed, a clock skew between the two (usually seconds with NTP) can route an issue created
 * just before the start, or miss one created just after it.
 */
public class NewIssuesConsumer extends AbstractJiraConsumer {
    private static final Logger LOG = LoggerFactory.getLogger(NewIssuesConsumer.class);
    // Date format used in the JQL below
    private static final DateTimeFormatter JIRA_DATE_FORMAT = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm");
    // Even the operator is ">", the JQL behaves more like ">=", so it will return all issues created at that minute and later
    private static final String NEW_ISSUES_JQL_FORMAT = "created > \"%s\" AND %s ORDER BY created DESC";

    // Last issue that was processed by the integration
    private Issue latestIssue;
    // Whether the latest issue was looked up when the consumer started. If not, the issues created after the start are
    // the new ones, as all issues that exist when the consumer starts are older
    private volatile boolean latestIssueLookedUp;
    // when the consumer started, by the local clock
    private DateTime startTime;
    // timezone of the current user authenticated
    private DateTimeZone userTimeZone;

    public NewIssuesConsumer(JiraEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    @Override
    protected void doStart() throws Exception {
        // before super.doStart(), which starts the polls: they use what is set here
        startTime = DateTime.now();
        latestIssue = null;
        latestIssueLookedUp = false;
        // also before the lookup: the polls that follow a failed lookup need it
        userTimeZone = getUserTimeZone();
        try {
            lookUpLatestIssue();
        } catch (Exception e) {
            // without the latest issue every existing issue would be taken as new: the polls ask for the issues created
            // since the start instead
            LOG.warn("Cannot find the latest issue at startup (the polls will ask for the issues created since the start): {}",
                    e.getMessage(), e);
        }

        super.doStart();
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
     * The start of the consumer in the timezone of the user, as the JQL expects it.
     *
     * @return timestamp string used in the JQL
     */
    private String getStartTimestamp() {
        return startTime.withZone(userTimeZone).toString(JIRA_DATE_FORMAT);
    }

    /**
     * Gets the timezone of the currently authenticated user. Basic auth configuration has precedence over OAuth
     * credentials as in the login process.
     *
     * @return {@link DateTimeZone} of the user
     */
    private DateTimeZone getUserTimeZone() {
        URI userURI = URI.create(getEndpoint().getConfiguration().getJiraUrl()).resolve("rest/api/latest/myself");
        final User user = getEndpoint().getClient().getUserClient().getUser(userURI).claim();
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
        // get the issues, ordered so that the latest one is the first one returned by the query
        Queue<Issue> issues = getIssues(getEndpoint().getJql() + " ORDER BY created DESC", 1);
        if (!issues.isEmpty()) {
            return issues.peek();
        }
        // in case there aren't any issues...
        return null;
    }

    private void lookUpLatestIssue() {
        latestIssue = findLatestIssue();
        latestIssueLookedUp = true;
        if (latestIssue != null) {
            LOG.debug("Init: Latest issue: {}", latestIssue.getKey());
        }
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
        Queue<Issue> issues;
        // If we have processed an issue before, use its timestamp to get only the newer ones
        if (latestIssue != null) {
            issues = getAllIssues(String.format(NEW_ISSUES_JQL_FORMAT, getServerTimestamp(), getEndpoint().getJql()));
        } else if (!latestIssueLookedUp) {
            // the lookup failed at startup: the issues created since the start are the new ones. The JQL returns the
            // issues of the minute of the start too, so keep those created after it
            issues = getAllIssues(String.format(NEW_ISSUES_JQL_FORMAT, getStartTimestamp(), getEndpoint().getJql()));
            issues.removeIf(i -> !i.getCreationDate().isAfter(startTime));
        } else {
            issues = getIssues(getEndpoint().getJql());
        }

        if (!issues.isEmpty()) {
            if (latestIssue != null) {
                // remove all issues that are older than the latestIssue (including), because those were processed by previous polls
                issues.removeIf(i -> i.getCreationDate().isBefore(latestIssue.getCreationDate()) || i.getCreationDate().isEqual(
                        latestIssue.getCreationDate()));
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

    /**
     * All the issues of the JQL: the newest one becomes the latest issue, so a new issue left out by maxResults would
     * never be returned again; maxResults is the page size here.
     */
    private Queue<Issue> getAllIssues(String jql) {
        int maxResults = getEndpoint().getMaxResults();
        int pageSize = maxResults > 0 ? Math.min(maxResults, SEARCH_MAX_PER_QUERY) : SEARCH_MAX_PER_QUERY;
        return getIssues(jql, SEARCH_START_AT, pageSize, Integer.MAX_VALUE);
    }
}
