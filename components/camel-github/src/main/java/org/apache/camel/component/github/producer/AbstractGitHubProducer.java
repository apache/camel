package org.apache.camel.component.github.producer;

import org.apache.camel.Exchange;
import org.apache.camel.component.github.GitHubEndpoint;
import org.apache.camel.impl.DefaultProducer;
import org.eclipse.egit.github.core.Repository;
import org.eclipse.egit.github.core.service.GitHubService;
import org.eclipse.egit.github.core.service.RepositoryService;

public abstract class AbstractGitHubProducer extends DefaultProducer {
    
    private final GitHubEndpoint endpoint;
    
    private RepositoryService repositoryService = null;
    
    private Repository repository = null;
    
    public AbstractGitHubProducer(GitHubEndpoint endpoint) throws Exception {
        super(endpoint);
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

    public abstract void process(Exchange exchange) throws Exception;
}
