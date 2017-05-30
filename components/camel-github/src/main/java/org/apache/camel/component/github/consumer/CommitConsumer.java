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

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.github.GitHubConstants;
import org.apache.camel.component.github.GitHubEndpoint;
import org.apache.camel.spi.Registry;
import org.eclipse.egit.github.core.RepositoryCommit;
import org.eclipse.egit.github.core.service.CommitService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CommitConsumer extends AbstractGitHubConsumer {
    private static final transient Logger LOG = LoggerFactory.getLogger(CommitConsumer.class);
    
    private CommitService commitService;
    
    private List<String> commitHashes = new ArrayList<String>();
    
    public CommitConsumer(GitHubEndpoint endpoint, Processor processor, String branchName) throws Exception {
        super(endpoint, processor);

        Registry registry = endpoint.getCamelContext().getRegistry();
        Object service = registry.lookupByName(GitHubConstants.GITHUB_COMMIT_SERVICE);
        if (service != null) {
            LOG.debug("Using CommitService found in registry " + service.getClass().getCanonicalName());
            commitService = (CommitService) service;
        } else {
            commitService = new CommitService();
        }
        initService(commitService);
        
        LOG.info("GitHub CommitConsumer: Indexing current commits...");
        List<RepositoryCommit> commits = commitService.getCommits(getRepository(), branchName, null);
        for (RepositoryCommit commit : commits) {
            commitHashes.add(commit.getSha());
        }
    }

    @Override
    protected int poll() throws Exception {
        List<RepositoryCommit> commits = commitService.getCommits(getRepository());
        // In the end, we want tags oldest to newest.
        Stack<RepositoryCommit> newCommits = new Stack<RepositoryCommit>();
        for (RepositoryCommit commit : commits) {
            if (!commitHashes.contains(commit.getSha())) {
                newCommits.push(commit);
                commitHashes.add(commit.getSha());
            }
        }
        
        while (!newCommits.empty()) {
            RepositoryCommit newCommit = newCommits.pop();
            Exchange e = getEndpoint().createExchange();
            e.getIn().setBody(newCommit);
            getProcessor().process(e);
        }
        return newCommits.size();
    }
}
