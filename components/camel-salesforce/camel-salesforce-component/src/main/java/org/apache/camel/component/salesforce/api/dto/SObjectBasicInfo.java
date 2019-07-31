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
package org.apache.camel.component.salesforce.api.dto;

import java.util.List;

import com.thoughtworks.xstream.annotations.XStreamImplicit;

public class SObjectBasicInfo extends AbstractDTOBase {

    private SObject objectDescribe;
    @XStreamImplicit
    private List<RecentItem> recentItems;

    public SObject getObjectDescribe() {
        return objectDescribe;
    }

    public void setObjectDescribe(SObject objectDescribe) {
        this.objectDescribe = objectDescribe;
    }

    public List<RecentItem> getRecentItems() {
        return recentItems;
    }

    public void setRecentItems(List<RecentItem> recentItems) {
        this.recentItems = recentItems;
    }

}
