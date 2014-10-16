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

import com.atlassian.jira.rest.client.NullProgressMonitor;
import com.atlassian.jira.rest.client.ProgressMonitor;
import com.atlassian.jira.rest.client.SearchRestClient;
import com.atlassian.jira.rest.client.domain.BasicIssue;
import com.atlassian.jira.rest.client.domain.FavouriteFilter;
import com.atlassian.jira.rest.client.domain.SearchResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class MockSearchRestClient implements SearchRestClient {
    private AtomicLong id = new AtomicLong(10001);
    private AtomicInteger index = new AtomicInteger(1);
    private String KEY_BASE="CAMELJIRA-";

    private final List<BasicIssue> issues = new ArrayList<>();

    @Override
    public SearchResult searchJql(String s, ProgressMonitor progressMonitor) {
        return null;
    }

    @Override
    public SearchResult searchJql(String s, int i, int i2, ProgressMonitor progressMonitor) {
        return null;
    }

    @Override
    public SearchResult searchJqlWithFullIssues(String s, int i, int i2, ProgressMonitor progressMonitor) {
        return new MockSearchResult(0, 1, issues.size(), issues);
    }

    @Override
    public Iterable<FavouriteFilter> getFavouriteFilters(NullProgressMonitor nullProgressMonitor) {
        return null;
    }

    public BasicIssue addIssue()  {
        String key = KEY_BASE + index.getAndIncrement();
        BasicIssue issue = new BasicIssue(null, key, id.getAndIncrement());
        issues.add(issue);
        return issue;
    }
}


