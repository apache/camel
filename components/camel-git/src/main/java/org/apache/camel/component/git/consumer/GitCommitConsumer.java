package org.apache.camel.component.git.consumer;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.git.GitEndpoint;
import org.eclipse.jgit.revwalk.RevCommit;

public class GitCommitConsumer extends AbstractGitConsumer {
	
	private List used;

	public GitCommitConsumer(GitEndpoint endpoint, Processor processor) {
		super(endpoint, processor);
		this.used = new ArrayList();
	}

	@Override
	protected int poll() throws Exception {
		int count = 0;
		Iterable<RevCommit> commits = getGit().log().all().call();
        for (RevCommit commit : commits) {
        	if (!used.contains(commit.getId())) {
            Exchange e = getEndpoint().createExchange();
            e.getOut().setBody(commit.getShortMessage());
            getProcessor().process(e);
            used.add(commit.getId());
            count++;
        	}
        }
        return count;
	}

}
