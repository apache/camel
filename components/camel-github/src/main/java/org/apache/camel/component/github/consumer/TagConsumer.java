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
import org.apache.camel.component.github.GitHubEndpoint;
import org.eclipse.egit.github.core.RepositoryTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TagConsumer extends AbstractGitHubConsumer {
    private static final transient Logger LOG = LoggerFactory.getLogger(TagConsumer.class);

    private List<String> tagNames = new ArrayList<String>();
    
    public TagConsumer(GitHubEndpoint endpoint, Processor processor) throws Exception {
        super(endpoint, processor);
        
        LOG.info("GitHub TagConsumer: Indexing current tags...");
        List<RepositoryTag> tags = getRepositoryService().getTags(getRepository());
        for (RepositoryTag tag : tags) {
            tagNames.add(tag.getName());
        }
    }

    @Override
    protected int poll() throws Exception {
        List<RepositoryTag> tags = getRepositoryService().getTags(getRepository());
        // In the end, we want tags oldest to newest.
        Stack<RepositoryTag> newTags = new Stack<RepositoryTag>();
        for (RepositoryTag tag : tags) {
            if (!tagNames.contains(tag.getName())) {
                newTags.push(tag);
                tagNames.add(tag.getName());
            }
        }
        
        while (!newTags.empty()) {
            RepositoryTag newTag = newTags.pop();
            Exchange e = getEndpoint().createExchange();
            e.getIn().setBody(newTag);
            getProcessor().process(e);
        }
        return newTags.size();
    }
}
