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
package org.apache.camel.component.wordpress.consumer;

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.camel.Processor;
import org.apache.camel.component.wordpress.WordpressEndpoint;
import org.apache.camel.component.wordpress.api.WordpressServiceProvider;
import org.apache.camel.component.wordpress.api.model.Post;
import org.apache.camel.component.wordpress.api.model.PostSearchCriteria;
import org.apache.camel.component.wordpress.api.service.WordpressServicePosts;

/**
 * Consumer for Posts. Adapter for {@link WordpressServicePosts} read only methods (list and retrieve).
 */
public class WordpressPostConsumer extends AbstractWordpressConsumer {

    private WordpressServicePosts servicePosts;

    public WordpressPostConsumer(WordpressEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        servicePosts = WordpressServiceProvider.getInstance().getService(WordpressServicePosts.class);
    }

    public WordpressPostConsumer(WordpressEndpoint endpoint, Processor processor, ScheduledExecutorService scheduledExecutorService) {
        super(endpoint, processor, scheduledExecutorService);
        servicePosts = WordpressServiceProvider.getInstance().getService(WordpressServicePosts.class);
    }

    @Override
    protected int poll() throws Exception {
        if (this.getConfiguration().getId() == null) {
            return this.pollForPostList();
        } else {
            return this.pollForSingle();
        }
    }

    private int pollForPostList() {
        final List<Post> posts = this.servicePosts.list((PostSearchCriteria)getConfiguration().getSearchCriteria());
        posts.stream().forEach(p -> this.process(p));
        return posts.size();
    }

    private int pollForSingle() {
        final Post post = this.servicePosts.retrieve(getConfiguration().getId());
        if (post == null) {
            return 0;
        }
        this.process(post);
        return 1;
    }
}
