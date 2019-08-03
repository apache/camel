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
package org.apache.camel.component.wordpress.producer;

import org.apache.camel.Exchange;
import org.apache.camel.component.wordpress.WordpressEndpoint;
import org.apache.camel.component.wordpress.api.WordpressServiceProvider;
import org.apache.camel.component.wordpress.api.model.Post;
import org.apache.camel.component.wordpress.api.service.WordpressServicePosts;

/**
 * The Wordpress Post producer.
 */
public class WordpressPostProducer extends AbstractWordpressProducer<Post> {
    private WordpressServicePosts servicePosts;

    public WordpressPostProducer(WordpressEndpoint endpoint) {
        super(endpoint);
        this.servicePosts = WordpressServiceProvider.getInstance().getService(WordpressServicePosts.class);
    }

    @Override
    protected Post processInsert(Exchange exchange) {
        LOG.debug("Trying to create a new blog post with {}", exchange.getIn().getBody());
        return servicePosts.create(exchange.getIn().getBody(Post.class));
    }

    @Override
    protected Post processUpdate(Exchange exchange) {
        LOG.debug("Trying to update the post {} with id {}", exchange.getIn().getBody(), this.getConfiguration().getId());
        return servicePosts.update(this.getConfiguration().getId(), exchange.getIn().getBody(Post.class));
    }

    @Override
    protected Post processDelete(Exchange exchange) {
        LOG.debug("Trying to delete a post with id {}", this.getConfiguration().getId());

        if (this.getConfiguration().isForce()) {
            return servicePosts.forceDelete(this.getConfiguration().getId()).getPrevious();
        } else {
            return servicePosts.delete(this.getConfiguration().getId());
        }
    }

}
