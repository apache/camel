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
import org.apache.camel.component.wordpress.api.model.PostRevision;
import org.apache.camel.component.wordpress.api.service.WordpressServicePostRevision;
import org.apache.camel.component.wordpress.api.service.spi.PostRevisionsSPI;

import static com.google.common.base.Preconditions.checkArgument;

public class WordpressSevicePostRevisionAdapter extends AbstractWordpressServiceAdapter<PostRevisionsSPI> implements WordpressServicePostRevision {

    public WordpressSevicePostRevisionAdapter(final String wordpressUrl, final String apiVersion) {
        super(wordpressUrl, apiVersion);
    }

    @Override
    protected Class<PostRevisionsSPI> getSpiType() {
        return PostRevisionsSPI.class;
    }

    @Override
    public void delete(Integer postId, Integer revisionId) {
        checkArgument(postId > 0, "Please define a post id");
        checkArgument(revisionId > 0, "Please define a revision id");
        this.getSpi().delete(this.getApiVersion(), postId, revisionId);
    }

    @Override
    public PostRevision retrieve(Integer postId, Integer revisionId, Context context) {
        checkArgument(postId > 0, "Please define a post id");
        checkArgument(revisionId > 0, "Please define a revision id");
        return this.getSpi().retrieveRevision(this.getApiVersion(), postId, revisionId, context);
    }

    @Override
    public List<PostRevision> list(Integer postId, Context context) {
        checkArgument(postId > 0, "Please define a post id");
        return this.getSpi().list(this.getApiVersion(), postId, context);
    }

}
