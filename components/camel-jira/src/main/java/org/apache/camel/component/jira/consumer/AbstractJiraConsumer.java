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

import java.util.ArrayList;
import java.util.List;

import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.SearchRestClient;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.SearchResult;
import org.apache.camel.Processor;
import org.apache.camel.component.jira.JiraEndpoint;
import org.apache.camel.support.ScheduledPollConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractJiraConsumer extends ScheduledPollConsumer {

    private static final transient Logger LOG = LoggerFactory.getLogger(AbstractJiraConsumer.class);

    private final JiraEndpoint endpoint;

    public AbstractJiraConsumer(JiraEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
        setDelay(endpoint.getDelay());
    }

    protected List<Issue> getIssues() {
        return getIssues(endpoint.getJql(), 0, 50, endpoint.getMaxResults());
    }

    // Ignore maxResults if it's <= 0.
    protected List<Issue> getIssues(String jql, int start, int maxPerQuery, int maxResults) {
        LOG.debug("Start indexing current JIRA issues...");

        List<Issue> issues = new ArrayList<>();
        while (true) {
            SearchRestClient searchRestClient = endpoint.getClient().getSearchClient();
            SearchResult searchResult = searchRestClient.searchJql(jql, maxResults, start, null).claim();

            for (Issue issue : searchResult.getIssues()) {
                issues.add(issue);
            }

            // Note: #getTotal == the total # the query would return *without* pagination, effectively telling us
            // we've reached the end. Also exit early if we're limiting the # of results.
            if (start >= searchResult.getTotal() || (maxResults > 0 && issues.size() >= maxResults)) {
                break;
            }

            start += maxPerQuery;
        }
        LOG.debug("End indexing current JIRA issues. {} issues indexed.", issues.size());
        return issues;
    }

    protected JiraRestClient client() {
        return endpoint.getClient();
    }

    @Override
    protected abstract int poll() throws Exception;
}
