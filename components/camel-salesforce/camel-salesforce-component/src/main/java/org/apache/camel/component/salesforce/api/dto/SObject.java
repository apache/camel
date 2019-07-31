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

public class SObject extends AbstractDTOBase {

    private String name;
    private String label;
    private Boolean updateable;
    private String keyPrefix;
    private Boolean custom;
    private SObjectUrls urls;
    private Boolean searchable;
    private String labelPlural;
    private Boolean layoutable;
    private Boolean activateable;
    private Boolean createable;
    private Boolean deprecatedAndHidden;
    private Boolean deletable;
    private Boolean customSetting;
    private Boolean feedEnabled;
    private String listviewable;
    private String lookupLayoutable;
    private Boolean mergeable;
    private Boolean queryable;
    private Boolean replicateable;
    private Boolean retrieveable;
    private String searchLayoutable;
    private Boolean undeletable;
    private Boolean triggerable;
    private Boolean compactLayoutable;
    private Boolean mruEnabled;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Boolean isUpdateable() {
        return updateable;
    }

    public void setUpdateable(Boolean updateable) {
        this.updateable = updateable;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public Boolean isCustom() {
        return custom;
    }

    public void setCustom(Boolean custom) {
        this.custom = custom;
    }

    public SObjectUrls getUrls() {
        return urls;
    }

    public void setUrls(SObjectUrls urls) {
        this.urls = urls;
    }

    public Boolean isSearchable() {
        return searchable;
    }

    public void setSearchable(Boolean searchable) {
        this.searchable = searchable;
    }

    public String getLabelPlural() {
        return labelPlural;
    }

    public void setLabelPlural(String labelPlural) {
        this.labelPlural = labelPlural;
    }

    public Boolean isLayoutable() {
        return layoutable;
    }

    public void setLayoutable(Boolean layoutable) {
        this.layoutable = layoutable;
    }

    public Boolean isActivateable() {
        return activateable;
    }

    public void setActivateable(Boolean activateable) {
        this.activateable = activateable;
    }

    public Boolean isCreateable() {
        return createable;
    }

    public void setCreateable(Boolean createable) {
        this.createable = createable;
    }

    public Boolean isDeprecatedAndHidden() {
        return deprecatedAndHidden;
    }

    public void setDeprecatedAndHidden(Boolean deprecatedAndHidden) {
        this.deprecatedAndHidden = deprecatedAndHidden;
    }

    public Boolean isDeletable() {
        return deletable;
    }

    public void setDeletable(Boolean deletable) {
        this.deletable = deletable;
    }

    public Boolean isCustomSetting() {
        return customSetting;
    }

    public void setCustomSetting(Boolean customSetting) {
        this.customSetting = customSetting;
    }

    public Boolean isFeedEnabled() {
        return feedEnabled;
    }

    public void setFeedEnabled(Boolean feedEnabled) {
        this.feedEnabled = feedEnabled;
    }

    public String getListviewable() {
        return listviewable;
    }

    public void setListviewable(String listviewable) {
        this.listviewable = listviewable;
    }

    public String getLookupLayoutable() {
        return lookupLayoutable;
    }

    public void setLookupLayoutable(String lookupLayoutable) {
        this.lookupLayoutable = lookupLayoutable;
    }

    public Boolean isMergeable() {
        return mergeable;
    }

    public void setMergeable(Boolean mergeable) {
        this.mergeable = mergeable;
    }

    public Boolean isQueryable() {
        return queryable;
    }

    public void setQueryable(Boolean queryable) {
        this.queryable = queryable;
    }

    public Boolean isReplicateable() {
        return replicateable;
    }

    public void setReplicateable(Boolean replicateable) {
        this.replicateable = replicateable;
    }

    public Boolean isRetrieveable() {
        return retrieveable;
    }

    public void setRetrieveable(Boolean retrieveable) {
        this.retrieveable = retrieveable;
    }

    public String getSearchLayoutable() {
        return searchLayoutable;
    }

    public void setSearchLayoutable(String searchLayoutable) {
        this.searchLayoutable = searchLayoutable;
    }

    public Boolean isUndeletable() {
        return undeletable;
    }

    public void setUndeletable(Boolean undeletable) {
        this.undeletable = undeletable;
    }

    public Boolean isTriggerable() {
        return triggerable;
    }

    public void setTriggerable(Boolean triggerable) {
        this.triggerable = triggerable;
    }

    public Boolean getCompactLayoutable() {
        return compactLayoutable;
    }

    public void setCompactLayoutable(Boolean compactLayoutable) {
        this.compactLayoutable = compactLayoutable;
    }

    public Boolean getMruEnabled() {
        return mruEnabled;
    }

    public void setMruEnabled(Boolean mruEnabled) {
        this.mruEnabled = mruEnabled;
    }
}
