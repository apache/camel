package org.apache.camel.component.git;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.impl.ScheduledPollConsumer;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

public class GitConsumer extends ScheduledPollConsumer {
	
	private GitEndpoint endpoint;
	
	private Repository repository;
	
	private Git git;
	
	private List used;

	public GitConsumer(GitEndpoint endpoint, Processor processor) {
		super(endpoint, processor);
		this.endpoint = endpoint;
		this.repository = getLocalRepository();
		this.git = new Git(repository);
		this.used = new ArrayList();
	}

	@Override
	protected int poll() throws Exception {
		int count = 0;
		Iterable<RevCommit> commits = git.log().all().call();
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
	
    private Repository getLocalRepository(){
        FileRepositoryBuilder builder = new FileRepositoryBuilder();
        Repository repo = null;
		try {
			repo = builder.setGitDir(new File(endpoint.getLocalPath(), ".git"))
			        .readEnvironment() // scan environment GIT_* variables
			        .findGitDir() // scan up the file system tree
			        .build();
		} catch (IOException e) {
			//LOG.error("There was an error, cannot open " + endpoint.getLocalPath() + " repository");
			e.printStackTrace();
		}
		return repo;
    }

}
