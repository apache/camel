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

import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.Queue;
import java.util.Set;

import com.atlassian.jira.rest.client.api.JiraRestClient;
import com.atlassian.jira.rest.client.api.RestClientException;
import com.atlassian.jira.rest.client.api.SearchRestClient;
import com.atlassian.jira.rest.client.api.domain.Issue;
import com.atlassian.jira.rest.client.api.domain.SearchResult;
import com.atlassian.jira.rest.client.internal.async.AsynchronousCloudSearchRestClient;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePropertyKey;
import org.apache.camel.Processor;
import org.apache.camel.component.jira.JiraEndpoint;
import org.apache.camel.support.EmptyAsyncCallback;
import org.apache.camel.support.ScheduledBatchPollingConsumer;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractJiraConsumer extends ScheduledBatchPollingConsumer {

    protected static final int SEARCH_START_AT = 0;
    protected static final int SEARCH_MAX_PER_QUERY = 50;

    private static final Logger LOG = LoggerFactory.getLogger(AbstractJiraConsumer.class);

    private final JiraEndpoint endpoint;

    protected AbstractJiraConsumer(JiraEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
        this.endpoint.setDelay(endpoint.getDelay());
    }

    @Override
    public JiraEndpoint getEndpoint() {
        return (JiraEndpoint) super.getEndpoint();
    }

    protected abstract int doPoll() throws Exception;

    @Override
    public int poll() throws Exception {
        try {
            return doPoll();
        } catch (Exception e) {
            RestClientException rcr = ObjectHelper.getException(RestClientException.class, e);
            if (rcr != null) {
                if (rcr.getStatusCode().isPresent()) {
                    int code = rcr.getStatusCode().get();
                    // if auth or server error then cause a re-connect
                    if (code >= 400) {
                        LOG.warn(
                                "RestClientException error code: {} caused by {}. Will re-connect on next poll.",
                                code,
                                rcr.getMessage());
                        getEndpoint().disconnect();
                    }
                }
            }
            throw e;
        }
    }

    @Override
    public int processBatch(Queue<Object> objects) throws Exception {
        int total = objects.size();

        for (int index = 0; index < total && isBatchAllowed(); index++) {
            // only loop if we are started (allowed to run)
            Object body = objects.poll();
            final Exchange exchange = createExchange(true);
            exchange.getIn().setBody(body);
            // add current index and total as properties
            exchange.setProperty(ExchangePropertyKey.BATCH_INDEX, index);
            exchange.setProperty(ExchangePropertyKey.BATCH_SIZE, total);
            exchange.setProperty(ExchangePropertyKey.BATCH_COMPLETE, index == total - 1);

            // update pending number of exchanges
            pendingExchanges = total - index - 1;

            getAsyncProcessor().process(exchange, EmptyAsyncCallback.get());
        }
        return total;
    }

    protected Queue<Issue> getIssues() {
        return getIssues(endpoint.getJql());
    }

    protected Queue<Issue> getIssues(String jql) {
        return getIssues(jql, endpoint.getMaxResults());
    }

    protected Queue<Issue> getIssues(String jql, int maxResults) {
        return getIssues(jql, SEARCH_START_AT, SEARCH_MAX_PER_QUERY, maxResults);
    }

    // Ignore maxResults if it's <= 0.
    protected Queue<Issue> getIssues(String jql, int start, int maxPerQuery, int maxResults) {
        LOG.debug("Start indexing current JIRA issues...");

        if (maxResults < maxPerQuery) {
            maxPerQuery = maxResults;
        }

        // Avoid duplicates
        Set<Issue> issues = new LinkedHashSet<>();
        while (true) {
            SearchRestClient searchRestClient = endpoint.getClient().getSearchClient();
            // *navigable should be the default value, but it does not seem to be true with 6.0.2 client
            SearchResult searchResult = searchRestClient
                    .searchJql(jql, maxPerQuery, start, Set.of("*navigable"))
                    .claim();

            for (Issue issue : searchResult.getIssues()) {
                issues.add(issue);
            }

            // Note: the total # the query would return *without* pagination, effectively telling us
            // we've reached the end. Also exit early if we're limiting the # of results or
            // if total # of returned issues is lower than the actual page size.
            int total;
            if (searchRestClient instanceof AsynchronousCloudSearchRestClient) {
                // calling searchResult.getTotal() on AsynchronousCloudSearchRestClient throws an exception:
                // Total is not available in the Cloud version of the new Search API response.
                // Please use `SearchRestClient.totalCount` instead to fetch the estimated count of the issues for a
                // given JQL
                total = searchRestClient.totalCount(jql).claim().getCount();
            } else {
                total = searchResult.getTotal();
            }
            if (maxPerQuery >= total || start >= total || maxResults > 0 && issues.size() >= maxResults) {
                break;
            }

            start += maxPerQuery;
        }
        LOG.debug("End indexing current JIRA issues. {} issues indexed.", issues.size());
        return new LinkedList<>(issues);
    }

    protected JiraRestClient client() {
        return endpoint.getClient();
    }
}
