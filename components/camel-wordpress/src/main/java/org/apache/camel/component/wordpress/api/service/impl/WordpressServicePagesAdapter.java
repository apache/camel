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

import org.apache.camel.component.wordpress.api.model.Context;
import org.apache.camel.component.wordpress.api.model.Page;
import org.apache.camel.component.wordpress.api.model.PageSearchCriteria;
import org.apache.camel.component.wordpress.api.service.WordpressServicePages;
import org.apache.camel.component.wordpress.api.service.WordpressServicePosts;
import org.apache.camel.component.wordpress.api.service.spi.PagesSPI;
import org.apache.camel.component.wordpress.api.service.spi.PostsSPI;
import org.apache.cxf.jaxrs.client.JAXRSClientFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;

/**
 * The {@link WordpressServicePosts} implementation. Aggregates the {@link PostsSPI} interface using {@link JAXRSClientFactory} to make the API calls.
 * 
 * @since 0.0.1
 */
public class WordpressServicePagesAdapter extends AbstractWordpressCrudServiceAdapter<PagesSPI, Page, PageSearchCriteria> implements WordpressServicePages {

    private static final Logger LOGGER = LoggerFactory.getLogger(WordpressServicePagesAdapter.class);

    public WordpressServicePagesAdapter(String wordpressUrl, String apiVersion) {
        super(wordpressUrl, apiVersion);
    }

    @Override
    protected Class<PagesSPI> getSpiType() {
        return PagesSPI.class;
    }

    // @formatter:off
    @Override
    public List<Page> list(PageSearchCriteria c) {
        LOGGER.debug("Calling list pages: searchCriteria {}", c);
        checkNotNull(c, "Please provide a search criteria");
        return getSpi().list(this.getApiVersion(), c.getContext(), c.getPage(), c.getPerPage(), c.getSearch(), c.getAfter(), c.getAuthor(), c.getAuthorExclude(), c.getBefore(), c.getExclude(),
                             c.getInclude(), c.getMenuOrder(), c.getOffset(), c.getOrder(), c.getOrderBy(), c.getParent(), c.getParentExclude(), c.getSlug(), c.getStatus(), c.getFilter());
    }
    // @formatter:on

    @Override
    public Page retrieve(Integer pageId, Context context, String password) {
        LOGGER.debug("Calling retrieve: postId {};  context: {}", pageId, context);
        checkArgument(pageId > 0, "Please provide a non zero post id");
        return getSpi().retrieve(this.getApiVersion(), pageId, context, password);
    }

    @Override
    protected Page doCreate(Page object) {
        return getSpi().create(getApiVersion(), object);
    }

    @Override
    protected Page doDelete(Integer id) {
        return getSpi().delete(getApiVersion(), id, false);
    }

    @Override
    protected Page doUpdate(Integer id, Page object) {
        return getSpi().update(getApiVersion(), id, object);
    }

    @Override
    protected Page doRetrieve(Integer entityID, Context context) {
        return getSpi().retrieve(getApiVersion(), entityID, context, null);
    }

}
