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

import org.apache.camel.component.wordpress.api.model.Category;
import org.apache.camel.component.wordpress.api.model.CategorySearchCriteria;
import org.apache.camel.component.wordpress.api.model.Context;
import org.apache.camel.component.wordpress.api.service.WordpressServiceCategories;
import org.apache.camel.component.wordpress.api.service.spi.CategoriesSPI;

import static com.google.common.base.Preconditions.checkNotNull;

public class WordpressServiceCategoriesAdapter extends AbstractWordpressCrudServiceAdapter<CategoriesSPI, Category, CategorySearchCriteria> implements WordpressServiceCategories {

    public WordpressServiceCategoriesAdapter(String wordpressUrl, String apiVersion) {
        super(wordpressUrl, apiVersion);
    }

    @Override
    protected Class<CategoriesSPI> getSpiType() {
        return CategoriesSPI.class;
    }

    // @formatter:off
    @Override
    public List<Category> list(CategorySearchCriteria criteria) {
        checkNotNull(criteria, "The search criteria must be defined");
        return getSpi().list(this.getApiVersion(), criteria.getContext(), criteria.getPage(), criteria.getPerPage(), criteria.getSearch(), criteria.getExclude(), criteria.getInclude(),
                             criteria.getOrder(), criteria.getOrderBy(), criteria.isHideEmpty(), criteria.getParent(), criteria.getPostId(), criteria.getSlug());
    }
    // @formatter:on

    @Override
    protected Category doCreate(Category object) {
        return getSpi().create(getApiVersion(), object);
    }

    @Override
    protected Category doDelete(Integer id) {
        return getSpi().delete(getApiVersion(), id, false);
    }

    @Override
    protected Category doUpdate(Integer id, Category object) {
        return getSpi().update(getApiVersion(), id, object);
    }

    @Override
    protected Category doRetrieve(Integer entityID, Context context) {
        return getSpi().retrieve(getApiVersion(), entityID, context);
    }

}
