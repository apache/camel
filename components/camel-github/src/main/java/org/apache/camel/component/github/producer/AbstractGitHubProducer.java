/*
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
package org.apache.camel.component.github.producer;

import org.apache.camel.Exchange;
import org.apache.camel.component.github.GitHubConstants;
import org.apache.camel.component.github.GitHubEndpoint;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.DefaultProducer;
import org.eclipse.egit.github.core.Repository;
import org.eclipse.egit.github.core.service.GitHubService;
import org.eclipse.egit.github.core.service.RepositoryService;

public abstract class AbstractGitHubProducer extends DefaultProducer {
    
    private final GitHubEndpoint endpoint;
    
    private RepositoryService repositoryService;
    
    private Repository repository;
    
    public AbstractGitHubProducer(GitHubEndpoint endpoint) throws Exception {
        super(endpoint);
        this.endpoint = endpoint;

        Registry registry = endpoint.getCamelContext().getRegistry();
        Object service = registry.lookupByName(GitHubConstants.GITHUB_REPOSITORY_SERVICE);
        if (service != null) {
            repositoryService = (RepositoryService) service;
        } else {
            repositoryService = new RepositoryService();
        }
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

    @Override
    public abstract void process(Exchange exchange) throws Exception;
}
