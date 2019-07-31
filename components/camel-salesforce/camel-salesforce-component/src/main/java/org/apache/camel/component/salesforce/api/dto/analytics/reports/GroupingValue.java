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

import org.apache.camel.component.salesforce.api.dto.AbstractDTOBase;

/**
 * Report results grouping value.
 */
public class GroupingValue extends AbstractDTOBase {

    private String value;
    private String key;
    private String label;
    private GroupingValue[] groupings;
    // TODO the description is vague about this!!!
    private GroupingValue[] dategroupings;

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public GroupingValue[] getGroupings() {
        return groupings;
    }

    public void setGroupings(GroupingValue[] groupings) {
        this.groupings = groupings;
    }

    public GroupingValue[] getDategroupings() {
        return dategroupings;
    }

    public void setDategroupings(GroupingValue[] dategroupings) {
        this.dategroupings = dategroupings;
    }
}
