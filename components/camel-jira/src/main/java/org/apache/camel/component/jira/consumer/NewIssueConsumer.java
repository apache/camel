package org.apache.camel.component.jira.consumer;

import java.util.List;
import java.util.Stack;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.jira.JIRAEndpoint;

import com.atlassian.jira.rest.client.domain.BasicIssue;

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
		while(!newIssues.empty()) {
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
