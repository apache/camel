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

import java.util.LinkedList;
import java.util.Queue;

import com.atlassian.jira.rest.client.api.RestClientException;
import com.atlassian.jira.rest.client.api.domain.Issue;
import org.apache.camel.Processor;
import org.apache.camel.component.jira.JiraEndpoint;
import org.apache.camel.util.CastUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Consumes new JIRA issues.
 *
 * NOTE: We manually add "ORDER BY key desc" to the JQL in order to optimize startup (the latest issues one at a time),
 * rather than having to index everything.
 */
public class NewIssuesConsumer extends AbstractJiraConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(NewIssuesConsumer.class);

    private final String jql;
    private long latestIssueId = -1;

    public NewIssuesConsumer(JiraEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        jql = endpoint.getJql() + " ORDER BY key desc";
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        // read the actual issues, the next poll outputs only the new issues added after the route start
        // grab only the top
        latestIssueId = findLatestIssueId();
    }

    protected long findLatestIssueId() {
        // read the actual issues, the next poll outputs only the new issues added after the route start
        // grab only the top
        try {
            Queue<Issue> issues = getIssues(jql, 1);
            if (!issues.isEmpty()) {
                // Issues returned are ordered descendant so this is the newest issue
                return issues.peek().getId();
            }
        } catch (Exception e) {
            // ignore
        }
        // in case there aren't any issues...
        return -1;
    }

    protected int doPoll() throws Exception {
        // it may happen the poll() is called while the route is doing the initial load,
        // this way we need to wait for the latestIssueId being associated to the last indexed issue id
        Queue<Issue> newIssues = getNewIssues();
        // In the end, we want only *new* issues oldest to newest. New issues returned are ordered descendant already.
        processBatch(CastUtils.cast(newIssues));
        return newIssues.size();
    }

    private Queue<Issue> getNewIssues() {
        String jqlFilter;
        if (latestIssueId > -1) {
            // search only for issues created after the latest id
            jqlFilter = "id > " + latestIssueId + " AND " + jql;
        } else {
            jqlFilter = jql;
        }
        // the last issue may be deleted, so to recover we re-find it and go from there
        Queue<Issue> issues;
        try {
            issues = getIssues(jqlFilter);
        } catch (RestClientException e) {
            if (e.getStatusCode().isPresent()) {
                int code = e.getStatusCode().get();
                if (code == 400) {
                    String msg = e.getMessage();
                    if (msg != null && msg.contains("does not exist for the field 'id'")) {
                        LOG.warn("Last issue id: {} no longer exists (could have been deleted)."
                                 + " Will recover by fetching last issue id from JIRA and try again on next poll",
                                latestIssueId);
                        latestIssueId = findLatestIssueId();
                        return new LinkedList<>();
                    }
                }
            }
            throw e;
        }

        if (!issues.isEmpty()) {
            // remember last id we have processed
            // issues are ordered descendant so save the first issue in the list as the newest
            latestIssueId = issues.element().getId();
        }
        return issues;
    }
}
