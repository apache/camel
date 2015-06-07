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
package org.apache.camel.component.jira.consumer;

import java.util.List;
import java.util.Stack;

import com.atlassian.jira.rest.client.domain.BasicIssue;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.jira.JIRAEndpoint;

/**
 * Consumes new JIRA issues.
 * 
 * NOTE: We manually add "ORDER BY key desc" to the JQL in order to optimize startup (the latest issues one at a time),
 * rather than having to index everything.
 */
public class NewIssueConsumer extends AbstractJIRAConsumer {

    private final String jql;

    private long latestIssueId = -1;

    public NewIssueConsumer(JIRAEndpoint endpoint, Processor processor) {
        super(endpoint, processor);

        jql = endpoint.getJql() + " ORDER BY key desc";

        // grab only the top
        List<BasicIssue> issues = getIssues(jql, 0, 1, 1);
        // in case there aren't any issues...
        if (issues.size() >= 1) {
            latestIssueId = issues.get(0).getId();
        }
    }

    @Override
    protected int poll() throws Exception {
        Stack<BasicIssue> newIssues = new Stack<BasicIssue>();
        getNewIssues(0, newIssues);
        while (!newIssues.empty()) {
            BasicIssue newIssue = newIssues.pop();
            Exchange e = getEndpoint().createExchange();
            e.getIn().setBody(newIssue);
            getProcessor().process(e);
        }
        return newIssues.size();
    }

    // In the end, we want *new* issues oldest to newest.
    private void getNewIssues(int start, Stack<BasicIssue> stack) {
        // grab only the top
        List<BasicIssue> issues = getIssues(jql, start, 1, 1);
        // in case there aren't any issues...
        if (issues.size() >= 1) {
            long id = issues.get(0).getId();
            if (id > latestIssueId) {
                stack.push(issues.get(0));
                // try again in case multiple new issues exist
                getNewIssues(start + 1, stack);
                // make sure this happens now, rather than before calling #getNewIssues
                latestIssueId = id;
            }
        }
    }
}
