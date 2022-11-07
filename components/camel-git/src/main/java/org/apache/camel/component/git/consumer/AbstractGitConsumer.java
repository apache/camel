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
package org.apache.camel.component.git.consumer;

import org.apache.camel.Processor;
import org.apache.camel.component.RepositoryFactory;
import org.apache.camel.component.git.GitEndpoint;
import org.apache.camel.support.ScheduledPollConsumer;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractGitConsumer extends ScheduledPollConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(AbstractGitConsumer.class);

    private final GitEndpoint endpoint;

    private Repository repo;

    private Git git;

    public AbstractGitConsumer(GitEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
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

    private Repository getLocalRepository() {
        return RepositoryFactory.of(endpoint);
    }

    public Repository getRepository() {
        return repo;
    }

    protected Git getGit() {
        return git;
    }

    @Override
    protected abstract int poll() throws Exception;
}
