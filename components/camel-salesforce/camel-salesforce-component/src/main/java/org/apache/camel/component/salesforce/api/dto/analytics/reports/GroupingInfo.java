/**
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
package org.apache.camel.component.salesforce.api.dto.analytics.reports;

import org.apache.camel.component.salesforce.api.dto.AbstractDTOBase;

/**
 * Report metadata grouping info.
 */
public class GroupingInfo extends AbstractDTOBase {

    private String name;
    private String sortAggregate;
    private ColumnSortOrderEnum sortOrder;
    private DateGranularityEnum dateGranularity;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSortAggregate() {
        return sortAggregate;
    }

    public void setSortAggregate(String sortAggregate) {
        this.sortAggregate = sortAggregate;
    }

    public ColumnSortOrderEnum getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(ColumnSortOrderEnum sortOrder) {
        this.sortOrder = sortOrder;
    }

    public DateGranularityEnum getDateGranularity() {
        return dateGranularity;
    }

    public void setDateGranularity(DateGranularityEnum dateGranularity) {
        this.dateGranularity = dateGranularity;
    }
}
