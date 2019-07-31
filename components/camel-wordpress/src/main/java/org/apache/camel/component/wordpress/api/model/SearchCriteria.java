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

import java.io.Serializable;
import java.util.List;

import static com.google.common.base.MoreObjects.toStringHelper;

/**
 * Search criterias.
 */
public class SearchCriteria implements Serializable {

    private static final long serialVersionUID = 1002576245120313648L;

    private Integer page;
    private Integer perPage;
    private String search;
    private Order order;
    private List<Integer> exclude;
    private List<Integer> include;

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getPerPage() {
        return perPage;
    }

    public void setPerPage(Integer perPage) {
        this.perPage = perPage;
    }

    public String getSearch() {
        return search;
    }

    public void setSearch(String search) {
        this.search = search;
    }

    public Order getOrder() {
        return order;
    }

    public void setOrder(Order order) {
        this.order = order;
    }

    public List<Integer> getExclude() {
        return exclude;
    }

    public void setExclude(List<Integer> exclude) {
        this.exclude = exclude;
    }

    public List<Integer> getInclude() {
        return include;
    }

    public void setInclude(List<Integer> include) {
        this.include = include;
    }

    @Override
    public String toString() {
        // @formatter:off
        return toStringHelper(this).add("Query", this.search).add("Page", page).add("Per Page", perPage).addValue(this.order).toString();
    }

}
