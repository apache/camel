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
package org.apache.camel.component.jira.producer;

import com.atlassian.jira.rest.client.domain.BasicIssue;
import com.atlassian.jira.rest.client.domain.input.IssueInputBuilder;
import org.apache.camel.Exchange;
import org.apache.camel.component.jira.JIRAEndpoint;

public class NewIssueProducer extends AbstractJIRAProducer {

    public NewIssueProducer(JIRAEndpoint endpoint) throws Exception {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        String projectKey = exchange.getIn().getHeader("ProjectKey", String.class);
        Long issueTypeId = exchange.getIn().getHeader("IssueTypeId", Long.class);
        String issueSummary = exchange.getIn().getHeader("IssueSummary", String.class);
        IssueInputBuilder issueBuilder = new IssueInputBuilder(projectKey, issueTypeId);
        issueBuilder.setDescription(exchange.getIn().getBody(String.class));
        issueBuilder.setSummary(issueSummary);
        BasicIssue issue = client().getIssueClient().createIssue(issueBuilder.build(), null);
        
        // support InOut
        if (exchange.getPattern().isOutCapable()) {
            // copy the header of in message to the out message
            exchange.getOut().copyFrom(exchange.getIn());
            exchange.getOut().setBody(issue);
        } else {
            exchange.getIn().setBody(issue);
        }
    }

}
