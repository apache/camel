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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import com.atlassian.jira.rest.client.api.domain.Issue;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.jira.JiraConstants;
import org.apache.camel.component.jira.JiraEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WatchUpdatesConsumer extends AbstractJiraConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(WatchUpdatesConsumer.class);
    private static final int SEARCH_MAX_PER_QUERY = 50;
    private static final int SEARCH_START_AT = 0;
    final HashMap<Long, Issue> watchedIssues = new HashMap<>();
    List<String> watchedFieldsList;
    String watchedIssuesKeys;

    public WatchUpdatesConsumer(JiraEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.watchedFieldsList = new ArrayList<>();
        this.watchedFieldsList = Arrays.asList(endpoint.getWatchedFields().split(","));
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        List<Issue> issues = getIssues(getEndpoint().getJql(), SEARCH_START_AT, SEARCH_MAX_PER_QUERY,
                getEndpoint().getMaxResults());
        initIssues(issues);
    }

    private void initIssues(List<Issue> issues) {
        watchedIssues.clear();
        issues.forEach(i -> watchedIssues.put(i.getId(), i));
        watchedIssuesKeys = issues.stream()
                .map(Issue::getKey)
                .collect(Collectors.joining(","));
    }

    @Override
    protected int doPoll() throws Exception {
        List<Issue> issues = getIssues(getEndpoint().getJql(), SEARCH_START_AT, SEARCH_MAX_PER_QUERY,
                getEndpoint().getMaxResults());
        for (Issue issue : issues) {
            checkIfIssueChanged(issue);
        }
        if (watchedIssues.values().size() != issues.size()) {
            // Rebuild the map of issues being watched
            initIssues(issues);
        }
        return 0;
    }

    private void checkIfIssueChanged(Issue issue) throws Exception {
        Issue original = watchedIssues.get(issue.getId());
        AtomicBoolean issueChanged = new AtomicBoolean();
        if (original != null) {
            for (String field : this.watchedFieldsList) {
                if (hasFieldChanged(issue, original, field)) {
                    issueChanged.set(true);
                }
            }
            if (issueChanged.get()) {
                watchedIssues.put(issue.getId(), issue);
            }
        }
    }

    private boolean hasFieldChanged(Issue changed, Issue original, String fieldName) throws Exception {
        Method get = Issue.class.getDeclaredMethod("get" + fieldName);
        Object originalField = get.invoke(original);
        Object changedField = get.invoke(changed);

        if (!Objects.equals(originalField, changedField)) {
            if (!getEndpoint().isSendOnlyUpdatedField()) {
                processExchange(changed, changed.getKey(), fieldName);
            } else {
                processExchange(changedField, changed.getKey(), fieldName);
            }
            return true;
        }
        return false;
    }

    private void processExchange(Object body, String issueKey, String changed) throws Exception {
        Exchange e = createExchange(true);
        e.getIn().setBody(body);
        e.getIn().setHeader(JiraConstants.ISSUE_KEY, issueKey);
        e.getIn().setHeader(JiraConstants.ISSUE_CHANGED, changed);
        e.getIn().setHeader(JiraConstants.ISSUE_WATCHED_ISSUES, watchedIssuesKeys);
        LOG.debug(" {}: {} changed to {}", issueKey, changed, body);
        getProcessor().process(e);
    }
}
