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
package org.apache.camel.component.wordpress.api.service.impl;

import java.util.List;
import java.util.Objects;

import org.apache.camel.component.wordpress.api.model.Context;
import org.apache.camel.component.wordpress.api.model.DeletedModel;
import org.apache.camel.component.wordpress.api.model.Post;
import org.apache.camel.component.wordpress.api.model.PostSearchCriteria;
import org.apache.camel.component.wordpress.api.service.WordpressServicePosts;
import org.apache.camel.component.wordpress.api.service.spi.PostsSPI;
import org.apache.cxf.jaxrs.client.JAXRSClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link WordpressServicePosts} implementation. Aggregates the {@link PostsSPI} interface using
 * {@link JAXRSClientFactory} to make the API calls.
 *
 * @since 0.0.1
 */
public class WordpressServicePostsAdapter extends AbstractWordpressCrudServiceAdapter<PostsSPI, Post, PostSearchCriteria>
        implements WordpressServicePosts {

    private static final Logger LOGGER = LoggerFactory.getLogger(WordpressServicePostsAdapter.class);

    public WordpressServicePostsAdapter(final String wordpressUrl, final String apiVersion) {
        super(wordpressUrl, apiVersion);
    }

    @Override
    protected Class<PostsSPI> getSpiType() {
        return PostsSPI.class;
    }

    @Override
    public List<Post> list(PostSearchCriteria criteria) {
        LOGGER.debug("Calling list posts: searchCriteria {}", criteria);
        Objects.requireNonNull(criteria, "Please provide a search criteria");
        return getSpi().list(this.getApiVersion(), criteria.getContext(), criteria.getPage(), criteria.getPerPage(),
                criteria.getSearch(), criteria.getAfter(), criteria.getAuthor(),
                criteria.getAuthorExclude(), criteria.getBefore(), criteria.getExclude(), criteria.getInclude(),
                criteria.getOffset(), criteria.getOrder(), criteria.getOrderBy(),
                criteria.getSlug(), criteria.getStatus(), criteria.getCategories(), criteria.getCategoriesExclude(),
                criteria.getTags(), criteria.getTagsExclude(), criteria.getStick());
    }

    @Override
    public Post retrieve(Integer postId, Context context, String password) {
        LOGGER.debug("Calling retrievePosts: postId {};  postContext: {}", postId, context);
        if (postId <= 0) {
            throw new IllegalArgumentException("Please provide a non zero post id");
        }
        Objects.requireNonNull(context, "Provide a post context");
        return getSpi().retrieve(this.getApiVersion(), postId, context, password);
    }

    @Override
    protected Post doRetrieve(Integer postId, Context context) {
        return this.retrieve(postId, context, "");
    }

    @Override
    public Post retrieve(Integer postId) {
        return this.retrieve(postId, Context.view, "");
    }

    @Override
    protected Post doCreate(Post object) {
        return getSpi().create(this.getApiVersion(), object);
    }

    @Override
    protected Post doDelete(Integer id) {
        return getSpi().delete(getApiVersion(), id);
    }

    @Override
    protected DeletedModel<Post> doForceDelete(Integer id) {
        return getSpi().forceDelete(getApiVersion(), id);
    }

    @Override
    protected Post doUpdate(Integer id, Post object) {
        return getSpi().update(getApiVersion(), id, object);
    }

}
