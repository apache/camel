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

import com.atlassian.jira.rest.client.api.SearchRestClient;
import com.atlassian.jira.rest.client.api.domain.SearchResult;
import com.atlassian.jira.rest.client.api.domain.TotalCount;
import com.atlassian.jira.rest.client.internal.async.AsynchronousCloudSearchRestClient;
import io.atlassian.util.concurrent.Promise;
import io.atlassian.util.concurrent.Promises;
import org.mockito.Mock;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;

/**
 * The same as {@link NewIssuesConsumerMoreThanMaxResultsTest} on Jira Cloud, whose search client cannot page by
 * startAt: like the real client of jira-rest-java-client 6.0.4, it throws UnsupportedOperationException for a startAt
 * other than 0, and pages with the next page token instead.
 */
public class NewIssuesConsumerMoreThanMaxResultsCloudTest extends NewIssuesConsumerMoreThanMaxResultsTest {

    @Mock
    private AsynchronousCloudSearchRestClient cloudSearchRestClient;

    @Override
    protected SearchRestClient searchClient() {
        // lenient: which of these the consumer calls is what the test is about
        lenient().when(cloudSearchRestClient.searchJql(any(), any(), any(), any())).then(invocation -> {
            Integer startAt = invocation.getArgument(2);
            if (startAt != null && startAt != 0) {
                throw new UnsupportedOperationException(
                        "This search API is not supported anymore. Please use this enhancedSearchJql");
            }
            return enhancedSearch(invocation.getArgument(0), invocation.getArgument(1), null);
        });
        lenient().when(cloudSearchRestClient.enhancedSearchJql(any(), any(), any(), any(), any()))
                .then(invocation -> enhancedSearch(invocation.getArgument(0), invocation.getArgument(1),
                        invocation.getArgument(2)));
        lenient().when(cloudSearchRestClient.totalCount(any()))
                .then(invocation -> Promises.promise(new TotalCount(total(invocation.getArgument(0)))));
        return cloudSearchRestClient;
    }

    private Promise<SearchResult> enhancedSearch(String jql, int maxPerQuery, String nextPageToken) {
        int startAt = nextPageToken == null ? 0 : Integer.parseInt(nextPageToken);
        int total = total(jql);
        String next = startAt + maxPerQuery < total ? String.valueOf(startAt + maxPerQuery) : null;
        return Promises.promise(new SearchResult(page(jql, startAt, maxPerQuery), next));
    }
}
