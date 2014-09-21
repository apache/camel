package org.apache.camel.component.jira.consumer;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.jira.JIRAEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.atlassian.jira.rest.client.domain.BasicIssue;
import com.atlassian.jira.rest.client.domain.Comment;
import com.atlassian.jira.rest.client.domain.Issue;

/**
 * Consumes new comments on JIRA issues.
 * 
 * NOTE: In your JQL, try to optimize the query as much as possible!  For example, the JIRA Toolkit Plugin includes a
// "Number of comments" custom field -- use '"Number of comments" > 0' in your query.  Also try to minimize based on
// state (status=Open), increase the polling delay, etc.  We have to do a separate query for *every single* resulting
 * ticket in order to load its comments!  For large organizations, the JIRA API can be significantly slow.
 */
public class NewCommentConsumer extends AbstractJIRAConsumer {
	private static final transient Logger LOG = LoggerFactory.getLogger(NewCommentConsumer.class);
	
    private List<Long> commentIds = new ArrayList<Long>();

	public NewCommentConsumer(JIRAEndpoint endpoint, Processor processor) {
		super(endpoint, processor);
		LOG.info("JIRA NewCommentConsumer: Indexing current issue comments...");
        getComments();
	}

	@Override
	protected int poll() throws Exception {
	    Stack<Comment> newComments = getComments();
	    while(!newComments.empty()) {
			Comment newComment = newComments.pop();
        	Exchange e = getEndpoint().createExchange();
            e.getIn().setBody(newComment);
            getProcessor().process(e);
        }
		return newComments.size();
	}
	
	// In the end, we want *new* comments oldest to newest.
	private Stack<Comment> getComments() {
	    Stack<Comment> newComments = new Stack<Comment>();
        List<BasicIssue> issues = getIssues();
        for (BasicIssue issue : issues) {
            Issue fullIssue = client().getIssueClient().getIssue(issue.getKey(), null);
            for (Comment comment : fullIssue.getComments()) {
                if (!commentIds.contains(comment.getId())) {
                    newComments.push(comment);
                    commentIds.add(comment.getId());
                }
            }
        }
        return newComments;
	}
}
