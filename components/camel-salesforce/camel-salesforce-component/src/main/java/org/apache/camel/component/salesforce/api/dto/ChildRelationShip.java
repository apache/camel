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

public class ChildRelationShip extends AbstractDTOBase {

    private String field;
    private Boolean deprecatedAndHidden;
    private String relationshipName;
    private Boolean cascadeDelete;
    private Boolean restrictedDelete;
    private String childSObject;
    private String junctionIdListName;
    private List<String> junctionReferenceTo;

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public Boolean isDeprecatedAndHidden() {
        return deprecatedAndHidden;
    }

    public void setDeprecatedAndHidden(Boolean deprecatedAndHidden) {
        this.deprecatedAndHidden = deprecatedAndHidden;
    }

    public String getRelationshipName() {
        return relationshipName;
    }

    public void setRelationshipName(String relationshipName) {
        this.relationshipName = relationshipName;
    }

    public Boolean isCascadeDelete() {
        return cascadeDelete;
    }

    public void setCascadeDelete(Boolean cascadeDelete) {
        this.cascadeDelete = cascadeDelete;
    }

    public Boolean isRestrictedDelete() {
        return restrictedDelete;
    }

    public void setRestrictedDelete(Boolean restrictedDelete) {
        this.restrictedDelete = restrictedDelete;
    }

    public String getChildSObject() {
        return childSObject;
    }

    public void setChildSObject(String childSObject) {
        this.childSObject = childSObject;
    }

    public String getJunctionIdListName() {
        return junctionIdListName;
    }

    public void setJunctionIdListName(String junctionIdListName) {
        this.junctionIdListName = junctionIdListName;
    }

    public List<String> getJunctionReferenceTo() {
        return junctionReferenceTo;
    }

    public void setJunctionReferenceTo(List<String> junctionReferenceTo) {
        this.junctionReferenceTo = junctionReferenceTo;
    }
}
