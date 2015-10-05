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
package org.apache.camel.component.git.consumer;

import java.util.ArrayList;
import java.util.List;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.git.GitEndpoint;
import org.eclipse.jgit.revwalk.RevCommit;

public class GitCommitConsumer extends AbstractGitConsumer {

    private List commitsConsumed = new ArrayList();

    public GitCommitConsumer(GitEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    @Override
    protected int poll() throws Exception {
        int count = 0;
        Iterable<RevCommit> commits = getGit().log().all().call();
        for (RevCommit commit : commits) {
            if (!commitsConsumed.contains(commit.getId())) {
                Exchange e = getEndpoint().createExchange();
                e.getOut().setBody(commit);
                getProcessor().process(e);
                commitsConsumed.add(commit.getId());
                count++;
            }
        }
        return count;
    }

}
