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

public class TagSearchCriteria extends ClassifierSearchCriteria {

    private static final long serialVersionUID = 3602397960341909720L;

    private List<Integer> offset;
    private TagOrderBy orderBy;

    public TagSearchCriteria() {

    }

    public List<Integer> getOffset() {
        return offset;
    }

    public void setOffset(List<Integer> offset) {
        this.offset = offset;
    }

    public TagOrderBy getOrderBy() {
        return orderBy;
    }

    public void setOrderBy(TagOrderBy orderBy) {
        this.orderBy = orderBy;
    }

}
