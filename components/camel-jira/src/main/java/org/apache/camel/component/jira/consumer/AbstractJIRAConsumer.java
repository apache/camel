package org.apache.camel.component.jira.consumer;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Processor;
import org.apache.camel.component.jira.JIRAEndpoint;
import org.apache.camel.impl.ScheduledPollConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.jira.rest.client.JiraRestClient;
import com.atlassian.jira.rest.client.domain.BasicIssue;
import com.atlassian.jira.rest.client.domain.SearchResult;
import com.atlassian.jira.rest.client.internal.jersey.JerseyJiraRestClientFactory;

public abstract class AbstractJIRAConsumer extends ScheduledPollConsumer {
	
	private static final transient Logger LOG = LoggerFactory.getLogger(AbstractJIRAConsumer.class);
	
	private final JIRAEndpoint endpoint;
    
    private final JiraRestClient client;
    
    public AbstractJIRAConsumer(JIRAEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
        
        // Use a more reasonable default.
        setDelay(6000);
        
        final JerseyJiraRestClientFactory factory = new JerseyJiraRestClientFactory();
		final URI jiraServerUri = URI.create(endpoint.getServerUrl());
		client = factory.createWithBasicHttpAuthentication(
				jiraServerUri, endpoint.getUsername(), endpoint.getPassword());
    }
    
    protected List<BasicIssue> getIssues() {
		return getIssues(endpoint.getJql(), 0, 0, 500);
    }
    
    // Ignore maxResults if it's <= 0.
    protected List<BasicIssue> getIssues(String jql, int start, int maxResults, int maxPerQuery) {
		LOG.info("Indexing current JIRA issues...");
		
		List<BasicIssue> issues = new ArrayList<BasicIssue>();
		while ( true ) {
			SearchResult searchResult = client.getSearchClient().searchJqlWithFullIssues(
					jql, maxPerQuery, start, null );

			for (BasicIssue issue : searchResult.getIssues()) {
				issues.add(issue);
			}

			// Note: #getTotal == the total # the query would return *without* pagination, effectively telling us
			// we've reached the end.  Also exit early if we're limiting the # of results.
			if ( start >= searchResult.getTotal() || 
					(maxResults > 0 && issues.size() >= maxResults)) {
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
