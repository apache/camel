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
package org.apache.camel.component.salesforce.api.dto.analytics.reports;

import java.util.Map;

import org.apache.camel.component.salesforce.api.dto.AbstractDTOBase;

/**
 * Report results extended metadata.
 */
public class ReportExtendedMetadata extends AbstractDTOBase {

    private Map<String, GroupingColumnInfo> groupingColumnInfo;
    private Map<String, DetailColumnInfo> detailColumnInfo;
    private Map<String, AggregateColumnInfo> aggregateColumnInfo;

    public Map<String, GroupingColumnInfo> getGroupingColumnInfo() {
        return groupingColumnInfo;
    }

    public void setGroupingColumnInfo(Map<String, GroupingColumnInfo> groupingColumnInfo) {
        this.groupingColumnInfo = groupingColumnInfo;
    }

    public Map<String, DetailColumnInfo> getDetailColumnInfo() {
        return detailColumnInfo;
    }

    public void setDetailColumnInfo(Map<String, DetailColumnInfo> detailColumnInfo) {
        this.detailColumnInfo = detailColumnInfo;
    }

    public Map<String, AggregateColumnInfo> getAggregateColumnInfo() {
        return aggregateColumnInfo;
    }

    public void setAggregateColumnInfo(Map<String, AggregateColumnInfo> aggregateColumnInfo) {
        this.aggregateColumnInfo = aggregateColumnInfo;
    }
}
