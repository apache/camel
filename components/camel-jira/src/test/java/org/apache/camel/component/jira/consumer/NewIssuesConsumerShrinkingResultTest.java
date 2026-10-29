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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.atlassian.jira.rest.client.api.domain.Issue;

/**
 * An issue leaves the result set between two pages of a poll on Jira Server / Data Center (deleted, moved, or edited
 * out of the JQL): the later issues move back by one, and the first issue of the next page must still be consumed.
 */
public class NewIssuesConsumerShrinkingResultTest extends NewIssuesConsumerMoreThanMaxResultsTest {

    private final Set<Issue> returned = new HashSet<>();
    private boolean removed;

    @Override
    protected int total(String jql) {
        if (!jql.startsWith("created >")) {
            return 1;
        }
        if (served.get()) {
            return 0;
        }
        return removed ? NEW_ISSUES - 1 : NEW_ISSUES;
    }

    @Override
    protected List<Issue> page(String jql, int startAt, int maxPerQuery) {
        if (!jql.startsWith("created >")) {
            return super.page(jql, startAt, maxPerQuery);
        }
        List<Issue> current = removed ? newIssues.subList(1, NEW_ISSUES) : newIssues;
        int total = served.get() ? 0 : current.size();
        List<Issue> answer = new ArrayList<>();
        for (int i = startAt; i < Math.min(startAt + maxPerQuery, total); i++) {
            answer.add(current.get(i));
        }
        // the newest issue, read on the first page, leaves the result set before the next page is read
        removed = true;
        returned.addAll(answer);
        if (returned.containsAll(current)) {
            served.set(true);
        }
        return answer;
    }
}
