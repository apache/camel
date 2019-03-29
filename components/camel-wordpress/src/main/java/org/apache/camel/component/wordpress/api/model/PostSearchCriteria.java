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
package org.apache.camel.component.wordpress.api.model;

import java.util.List;

public class PostSearchCriteria extends PublishableSearchCriteria {

    private static final long serialVersionUID = 2663161640460268421L;

    private List<String> categories;
    private List<String> categoriesExclude;
    private List<String> tags;
    private List<String> tagsExclude;
    private Boolean stick;
    private PostOrderBy orderBy;

    public PostOrderBy getOrderBy() {
        return orderBy;
    }

    public void setOrderBy(PostOrderBy orderBy) {
        this.orderBy = orderBy;
    }

    public List<String> getCategories() {
        return categories;
    }

    public void setCategories(List<String> categories) {
        this.categories = categories;
    }

    public List<String> getCategoriesExclude() {
        return categoriesExclude;
    }

    public void setCategoriesExclude(List<String> categoriesExclude) {
        this.categoriesExclude = categoriesExclude;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public List<String> getTagsExclude() {
        return tagsExclude;
    }

    public void setTagsExclude(List<String> tagsExclude) {
        this.tagsExclude = tagsExclude;
    }

    public Boolean getStick() {
        return stick;
    }

    public void setStick(Boolean stick) {
        this.stick = stick;
    }

}
