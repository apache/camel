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
package org.apache.camel.component.salesforce.api.dto;

import com.thoughtworks.xstream.annotations.XStreamConverter;

import org.apache.camel.component.salesforce.api.PicklistEnumConverter;

public class ActionOverride extends AbstractDTOBase {

    private String actionName;

    private String comment;

    private String content;

    private Boolean skipRecordTypeSelect;

    @XStreamConverter(PicklistEnumConverter.class)
    private ActionOverrideTypeEnum type;

    public String getActionName() {
        return actionName;
    }

    public void setActionName(String actionName) {
        this.actionName = actionName;
    }

    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Boolean getSkipRecordTypeSelect() {
        return skipRecordTypeSelect;
    }

    public void setSkipRecordTypeSelect(Boolean skipRecordTypeSelect) {
        this.skipRecordTypeSelect = skipRecordTypeSelect;
    }

    public ActionOverrideTypeEnum getType() {
        return type;
    }

    public void setType(ActionOverrideTypeEnum type) {
        this.type = type;
    }
}
