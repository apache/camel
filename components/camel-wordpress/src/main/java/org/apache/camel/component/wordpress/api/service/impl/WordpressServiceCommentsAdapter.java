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

import org.apache.camel.component.wordpress.api.model.Comment;
import org.apache.camel.component.wordpress.api.model.CommentSearchCriteria;
import org.apache.camel.component.wordpress.api.model.Context;
import org.apache.camel.component.wordpress.api.service.WordpressServiceComments;
import org.apache.camel.component.wordpress.api.service.spi.CommentsSPI;

public class WordpressServiceCommentsAdapter
        extends AbstractWordpressCrudServiceAdapter<CommentsSPI, Comment, CommentSearchCriteria>
        implements WordpressServiceComments {

    public WordpressServiceCommentsAdapter(final String wordpressUrl, final String apiVersion) {
        super(wordpressUrl, apiVersion);
    }

    @Override
    protected Class<CommentsSPI> getSpiType() {
        return CommentsSPI.class;
    }

    @Override
    public List<Comment> list(CommentSearchCriteria c) {
        Objects.requireNonNull(c, "The search criteria must be defined");
        return getSpi().list(this.getApiVersion(), c.getContext(), c.getPage(), c.getPerPage(), c.getSearch(), c.getAfter(),
                c.getAuthor(), c.getAuthorExclude(), c.getAuthorEmail(), c.getBefore(),
                c.getExclude(), c.getInclude(), c.getKarma(), c.getOffset(), c.getOrder(), c.getOrderBy(), c.getParent(),
                c.getParentExclude(), c.getPost(), c.getStatus(), c.getType());
    }

    @Override
    protected Comment doCreate(Comment object) {
        return getSpi().create(getApiVersion(), object);
    }

    @Override
    protected Comment doDelete(Integer id) {
        return getSpi().delete(getApiVersion(), id, false);
    }

    @Override
    protected Comment doUpdate(Integer id, Comment object) {
        return getSpi().update(getApiVersion(), id, object);
    }

    @Override
    protected Comment doRetrieve(Integer entityID, Context context) {
        return getSpi().retrieve(getApiVersion(), entityID, context);
    }

}
