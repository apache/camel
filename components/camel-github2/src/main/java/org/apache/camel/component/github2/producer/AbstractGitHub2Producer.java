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
package org.apache.camel.component.github2.producer;

import org.apache.camel.Exchange;
import org.apache.camel.component.github2.GitHub2Constants;
import org.apache.camel.component.github2.GitHub2Endpoint;
import org.apache.camel.component.github2.GitHubClientFactory;
import org.apache.camel.support.DefaultProducer;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHub;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractGitHub2Producer extends DefaultProducer {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractGitHub2Producer.class);

    private final GitHub2Endpoint endpoint;
    private GitHub github;
    private GHRepository repository;

    protected AbstractGitHub2Producer(GitHub2Endpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();

        // Check registry for existing GitHub client (useful for testing)
        github = endpoint.getCamelContext().getRegistry()
                .lookupByNameAndType(GitHub2Constants.GITHUB_CLIENT, GitHub.class);

        if (github == null) {
            LOG.debug("Creating GitHub client for endpoint");
            github = GitHubClientFactory.createClient(
                    endpoint.getOauthToken(),
                    endpoint.getApiUrl());
        } else {
            LOG.debug("Using GitHub client found in registry");
        }

        repository = github.getRepository(
                endpoint.getRepoOwner() + "/" + endpoint.getRepoName());
    }

    @Override
    public GitHub2Endpoint getEndpoint() {
        return (GitHub2Endpoint) super.getEndpoint();
    }

    protected GitHub getGitHub() {
        return github;
    }

    protected GHRepository getRepository() {
        return repository;
    }

    @Override
    public abstract void process(Exchange exchange) throws Exception;
}
