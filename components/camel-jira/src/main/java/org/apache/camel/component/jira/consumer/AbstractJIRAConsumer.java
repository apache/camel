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

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import com.atlassian.jira.rest.client.JiraRestClient;
import com.atlassian.jira.rest.client.SearchRestClient;
import com.atlassian.jira.rest.client.domain.BasicIssue;
import com.atlassian.jira.rest.client.domain.SearchResult;
import com.atlassian.jira.rest.client.internal.jersey.JerseyJiraRestClientFactory;


import org.apache.camel.Processor;
import org.apache.camel.component.jira.JIRAEndpoint;
import org.apache.camel.impl.ScheduledPollConsumer;
import org.apache.camel.spi.Registry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractJIRAConsumer extends ScheduledPollConsumer {

    private static final transient Logger LOG = LoggerFactory.getLogger(AbstractJIRAConsumer.class);

    private final JIRAEndpoint endpoint;
    
    private final JiraRestClient client;
    
    public AbstractJIRAConsumer(JIRAEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
        
        // support to set the delay from JIRA Endpoint
        setDelay(endpoint.getDelay());

        JerseyJiraRestClientFactory factory;
        Registry registry = endpoint.getCamelContext().getRegistry();
        Object target = registry.lookupByName("JerseyJiraRestClientFactory");
        if (target != null) {
            LOG.debug("JerseyJiraRestClientFactory found in registry " + target.getClass().getCanonicalName());
            factory = (JerseyJiraRestClientFactory) target;
        } else {
            factory = new JerseyJiraRestClientFactory();
        }


        final URI jiraServerUri = URI.create(endpoint.getServerUrl());
        client = factory.createWithBasicHttpAuthentication(jiraServerUri, endpoint.getUsername(),
                                                           endpoint.getPassword());
    }
    
    protected List<BasicIssue> getIssues() {
        return getIssues(endpoint.getJql(), 0, 0, 500);
    }
    
    // Ignore maxResults if it's <= 0.
    protected List<BasicIssue> getIssues(String jql, int start, int maxResults, int maxPerQuery) {
        LOG.info("Indexing current JIRA issues...");

        List<BasicIssue> issues = new ArrayList<BasicIssue>();
        while (true) {
            SearchRestClient searchRestClient = client.getSearchClient();
            SearchResult searchResult = searchRestClient.searchJqlWithFullIssues(jql, maxPerQuery, start, null);

            for (BasicIssue issue : searchResult.getIssues()) {
                issues.add(issue);
            }

            // Note: #getTotal == the total # the query would return *without* pagination, effectively telling us
            // we've reached the end. Also exit early if we're limiting the # of results.
            if (start >= searchResult.getTotal() || (maxResults > 0 && issues.size() >= maxResults)) {
                break;
            }

            start += maxPerQuery;
        }

        return issues;
    }
    
    protected JiraRestClient client() {
        return client;
    }

    protected abstract int poll() throws Exception;
}
