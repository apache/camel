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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;

import com.atlassian.jira.rest.client.domain.BasicIssue;
import com.atlassian.jira.rest.client.domain.SearchResult;

public class MockSearchResult extends SearchResult {
    private ArrayList<? extends BasicIssue> issues;
    private BasicIssueComparator basicIssueComparator = new BasicIssueComparator();


    public MockSearchResult(int startIndex, int maxResults, int total, java.lang.Iterable<? extends BasicIssue> issues) {
        super(startIndex, maxResults, total, issues);
        this.issues = (ArrayList<? extends BasicIssue>) issues;
    }


    @Override
    public int getTotal() {
        return issues.size();
    }


    @Override
    public Iterable<? extends BasicIssue> getIssues() {
        Collections.sort(issues, basicIssueComparator);
        ArrayList<? extends BasicIssue> copy = new ArrayList<BasicIssue>(issues);
        if (!issues.isEmpty()) {
            issues.remove(0);
        }
        return copy;
    }


    public class BasicIssueComparator implements Comparator<BasicIssue> {
        @Override
        public int compare(BasicIssue issue1, BasicIssue issue2) {
            if (issue1.getId() < issue2.getId()) {
                return 1;
            } else if (issue1.getId() == issue2.getId()) {
                return 0;
            } else {
                return -1;
            }
        }
    }
}
