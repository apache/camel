package org.apache.camel.component.git.consumer;

import java.io.File;
import java.io.IOException;

import org.apache.camel.Processor;
import org.apache.camel.component.git.GitEndpoint;
import org.apache.camel.impl.ScheduledPollConsumer;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

public abstract class AbstractGitConsumer extends ScheduledPollConsumer {
    
    private final GitEndpoint endpoint;
    
    private Repository repo;
    
    private Git git;

    public AbstractGitConsumer(GitEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
        this.repo = getLocalRepository();
        this.git = new Git(repo);
    }
    
    @Override
    protected void doStart() throws Exception {
        super.doStart();
        this.repo = getLocalRepository();
        this.git = new Git(repo);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
        repo.close();
        git.close();
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
    
    protected Repository getRepository() {
        return repo;
    }
    
    protected Git getGit() {
        return git;
    }
    
    protected abstract int poll() throws Exception;
}
