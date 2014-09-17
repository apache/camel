package org.apache.camel.component.github.consumer;

import org.apache.camel.Processor;
import org.apache.camel.component.github.GitHubEndpoint;
import org.apache.camel.impl.ScheduledPollConsumer;
import org.eclipse.egit.github.core.Repository;
import org.eclipse.egit.github.core.service.GitHubService;
import org.eclipse.egit.github.core.service.RepositoryService;

public abstract class AbstractGitHubConsumer extends ScheduledPollConsumer {
    
    private final GitHubEndpoint endpoint;
    
    private RepositoryService repositoryService = null;
    
    private Repository repository = null;

    public AbstractGitHubConsumer(GitHubEndpoint endpoint, Processor processor) throws Exception {
        super(endpoint, processor);
        this.endpoint = endpoint;
        
        repositoryService = new RepositoryService();
        initService(repositoryService);
        repository = repositoryService.getRepository(endpoint.getRepoOwner(), endpoint.getRepoName());
    }
    
    protected void initService(GitHubService service) {
        if (endpoint.hasOauth()) {
            service.getClient().setOAuth2Token(endpoint.getOauthToken());
        } else {
            service.getClient().setCredentials(endpoint.getUsername(), endpoint.getPassword());
        }
    }
    
    protected RepositoryService getRepositoryService() {
        return repositoryService;
    }
    
    protected Repository getRepository() {
        return repository;
    }

    protected abstract int poll() throws Exception;
}
