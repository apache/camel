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
package org.apache.camel.component.github.consumer;

import org.apache.camel.Processor;
import org.apache.camel.component.github.GitHubConstants;
import org.apache.camel.component.github.GitHubEndpoint;
import org.apache.camel.impl.ScheduledPollConsumer;
import org.apache.camel.spi.Registry;
import org.eclipse.egit.github.core.Repository;
import org.eclipse.egit.github.core.service.GitHubService;
import org.eclipse.egit.github.core.service.RepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractGitHubConsumer extends ScheduledPollConsumer {
    private static final transient Logger LOG = LoggerFactory.getLogger(AbstractGitHubConsumer.class);

    private final GitHubEndpoint endpoint;
    
    private RepositoryService repositoryService;
    
    private Repository repository;

    public AbstractGitHubConsumer(GitHubEndpoint endpoint, Processor processor) throws Exception {
        super(endpoint, processor);
        this.endpoint = endpoint;

        Registry registry = endpoint.getCamelContext().getRegistry();
        Object service = registry.lookupByName(GitHubConstants.GITHUB_REPOSITORY_SERVICE);
        if (service != null) {
            LOG.debug("Using RepositoryService found in registry " + service.getClass().getCanonicalName());
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

    protected abstract int poll() throws Exception;
}
