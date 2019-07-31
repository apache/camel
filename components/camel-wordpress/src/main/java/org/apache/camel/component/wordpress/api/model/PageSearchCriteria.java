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

public class PageSearchCriteria extends PublishableSearchCriteria {

    private static final long serialVersionUID = -166997518779286003L;

    private Integer menuOrder;
    private Integer parent;
    private Integer parentExclude;
    private String filter;
    private PageOrderBy orderBy;

    public PageOrderBy getOrderBy() {
        return orderBy;
    }

    public void setOrderBy(PageOrderBy orderBy) {
        this.orderBy = orderBy;
    }

    public Integer getMenuOrder() {
        return menuOrder;
    }

    public void setMenuOrder(Integer menuOrder) {
        this.menuOrder = menuOrder;
    }

    public Integer getParent() {
        return parent;
    }

    public void setParent(Integer parent) {
        this.parent = parent;
    }

    public Integer getParentExclude() {
        return parentExclude;
    }

    public void setParentExclude(Integer parentExclude) {
        this.parentExclude = parentExclude;
    }

    public String getFilter() {
        return filter;
    }

    public void setFilter(String filter) {
        this.filter = filter;
    }

}
