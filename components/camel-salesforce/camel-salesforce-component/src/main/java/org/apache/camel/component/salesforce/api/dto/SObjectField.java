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

public class SObjectField extends AbstractDTOBase {

    private Integer length;
    private String name;
    private String type;
    private String defaultValue;
    private String label;
    private Boolean updateable;
    private Boolean calculated;
    private Boolean caseSensitive;
    private String controllerName;
    private Boolean unique;
    private Boolean nillable;
    private Integer precision;
    private Integer scale;
    private Integer byteLength;
    private Boolean nameField;
    private Boolean sortable;
    private Boolean filterable;
    private Boolean writeRequiresMasterRead;
    private Boolean externalId;
    private Boolean idLookup;
    private String inlineHelpText;
    private Boolean createable;
    private String soapType;
    private Boolean autoNumber;
    private Boolean restrictedPicklist;
    private Boolean namePointing;
    private Boolean custom;
    private Boolean defaultedOnCreate;
    private Boolean deprecatedAndHidden;
    private Boolean htmlFormatted;
    private String defaultValueFormula;
    private String calculatedFormula;
    @XStreamImplicit
    private List<PickListValue> picklistValues;
    private Boolean dependentPicklist;
    @XStreamImplicit
    private List<String> referenceTo;
    private String relationshipName;
    private String relationshipOrder;
    private Boolean cascadeDelete;
    private Boolean restrictedDelete;
    private String digits;
    private Boolean groupable;
    private Boolean permissionable;
    private Boolean displayLocationInDecimal;
    private String extraTypeInfo;
    private FilteredLookupInfo filteredLookupInfo;
    private Boolean highScaleNumber;
    private String mask;
    private String maskType;
    private Boolean queryByDistance;
    private String referenceTargetField;
    private Boolean encrypted;

    public Integer getLength() {
        return length;
    }

    public void setLength(Integer length) {
        this.length = length;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
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

    public Boolean isCalculated() {
        return calculated;
    }

    public void setCalculated(Boolean calculated) {
        this.calculated = calculated;
    }

    public Boolean isCaseSensitive() {
        return caseSensitive;
    }

    public void setCaseSensitive(Boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
    }

    public String getControllerName() {
        return controllerName;
    }

    public void setControllerName(String controllerName) {
        this.controllerName = controllerName;
    }

    public Boolean isUnique() {
        return unique;
    }

    public void setUnique(Boolean unique) {
        this.unique = unique;
    }

    public Boolean isNillable() {
        return nillable;
    }

    public void setNillable(Boolean nillable) {
        this.nillable = nillable;
    }

    public Integer getPrecision() {
        return precision;
    }

    public void setPrecision(Integer precision) {
        this.precision = precision;
    }

    public Integer getScale() {
        return scale;
    }

    public void setScale(Integer scale) {
        this.scale = scale;
    }

    public Integer getByteLength() {
        return byteLength;
    }

    public void setByteLength(Integer byteLength) {
        this.byteLength = byteLength;
    }

    public Boolean isNameField() {
        return nameField;
    }

    public void setNameField(Boolean nameField) {
        this.nameField = nameField;
    }

    public Boolean isSortable() {
        return sortable;
    }

    public void setSortable(Boolean sortable) {
        this.sortable = sortable;
    }

    public Boolean isFilterable() {
        return filterable;
    }

    public void setFilterable(Boolean filterable) {
        this.filterable = filterable;
    }

    public Boolean isWriteRequiresMasterRead() {
        return writeRequiresMasterRead;
    }

    public void setWriteRequiresMasterRead(Boolean writeRequiresMasterRead) {
        this.writeRequiresMasterRead = writeRequiresMasterRead;
    }

    public Boolean isExternalId() {
        return externalId;
    }

    public void setExternalId(Boolean externalId) {
        this.externalId = externalId;
    }

    public Boolean isIdLookup() {
        return idLookup;
    }

    public void setIdLookup(Boolean idLookup) {
        this.idLookup = idLookup;
    }

    public String getInlineHelpText() {
        return inlineHelpText;
    }

    public void setInlineHelpText(String inlineHelpText) {
        this.inlineHelpText = inlineHelpText;
    }

    public Boolean isCreateable() {
        return createable;
    }

    public void setCreateable(Boolean createable) {
        this.createable = createable;
    }

    public String getSoapType() {
        return soapType;
    }

    public void setSoapType(String soapType) {
        this.soapType = soapType;
    }

    public Boolean isAutoNumber() {
        return autoNumber;
    }

    public void setAutoNumber(Boolean autoNumber) {
        this.autoNumber = autoNumber;
    }

    public Boolean isRestrictedPicklist() {
        return restrictedPicklist;
    }

    public void setRestrictedPicklist(Boolean restrictedPicklist) {
        this.restrictedPicklist = restrictedPicklist;
    }

    public Boolean isNamePointing() {
        return namePointing;
    }

    public void setNamePointing(Boolean namePointing) {
        this.namePointing = namePointing;
    }

    public Boolean isCustom() {
        return custom;
    }

    public void setCustom(Boolean custom) {
        this.custom = custom;
    }

    public Boolean isDefaultedOnCreate() {
        return defaultedOnCreate;
    }

    public void setDefaultedOnCreate(Boolean defaultedOnCreate) {
        this.defaultedOnCreate = defaultedOnCreate;
    }

    public Boolean isDeprecatedAndHidden() {
        return deprecatedAndHidden;
    }

    public void setDeprecatedAndHidden(Boolean deprecatedAndHidden) {
        this.deprecatedAndHidden = deprecatedAndHidden;
    }

    public Boolean isHtmlFormatted() {
        return htmlFormatted;
    }

    public void setHtmlFormatted(Boolean htmlFormatted) {
        this.htmlFormatted = htmlFormatted;
    }

    public String getDefaultValueFormula() {
        return defaultValueFormula;
    }

    public void setDefaultValueFormula(String defaultValueFormula) {
        this.defaultValueFormula = defaultValueFormula;
    }

    public String getCalculatedFormula() {
        return calculatedFormula;
    }

    public void setCalculatedFormula(String calculatedFormula) {
        this.calculatedFormula = calculatedFormula;
    }

    public List<PickListValue> getPicklistValues() {
        return picklistValues;
    }

    public void setPicklistValues(List<PickListValue> picklistValues) {
        this.picklistValues = picklistValues;
    }

    public Boolean isDependentPicklist() {
        return dependentPicklist;
    }

    public void setDependentPicklist(Boolean dependentPicklist) {
        this.dependentPicklist = dependentPicklist;
    }

    public List<String> getReferenceTo() {
        return referenceTo;
    }

    public void setReferenceTo(List<String> referenceTo) {
        this.referenceTo = referenceTo;
    }

    public String getRelationshipName() {
        return relationshipName;
    }

    public void setRelationshipName(String relationshipName) {
        this.relationshipName = relationshipName;
    }

    public String getRelationshipOrder() {
        return relationshipOrder;
    }

    public void setRelationshipOrder(String relationshipOrder) {
        this.relationshipOrder = relationshipOrder;
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

    public String getDigits() {
        return digits;
    }

    public void setDigits(String digits) {
        this.digits = digits;
    }

    public Boolean isGroupable() {
        return groupable;
    }

    public void setGroupable(Boolean groupable) {
        this.groupable = groupable;
    }

    public Boolean isPermissionable() {
        return permissionable;
    }

    public void setPermissionable(Boolean permissionable) {
        this.permissionable = permissionable;
    }

    public Boolean isDisplayLocationInDecimal() {
        return displayLocationInDecimal;
    }

    public void setDisplayLocationInDecimal(Boolean displayLocationInDecimal) {
        this.displayLocationInDecimal = displayLocationInDecimal;
    }

    public String getExtraTypeInfo() {
        return extraTypeInfo;
    }

    public void setExtraTypeInfo(String extraTypeInfo) {
        this.extraTypeInfo = extraTypeInfo;
    }

    public FilteredLookupInfo getFilteredLookupInfo() {
        return filteredLookupInfo;
    }

    public void setFilteredLookupInfo(FilteredLookupInfo filteredLookupInfo) {
        this.filteredLookupInfo = filteredLookupInfo;
    }

    public Boolean getHighScaleNumber() {
        return highScaleNumber;
    }

    public void setHighScaleNumber(Boolean highScaleNumber) {
        this.highScaleNumber = highScaleNumber;
    }

    public String getMask() {
        return mask;
    }

    public void setMask(String mask) {
        this.mask = mask;
    }

    public String getMaskType() {
        return maskType;
    }

    public void setMaskType(String maskType) {
        this.maskType = maskType;
    }

    public Boolean getQueryByDistance() {
        return queryByDistance;
    }

    public void setQueryByDistance(Boolean queryByDistance) {
        this.queryByDistance = queryByDistance;
    }

    public String getReferenceTargetField() {
        return referenceTargetField;
    }

    public void setReferenceTargetField(String referenceTargetField) {
        this.referenceTargetField = referenceTargetField;
    }

    public Boolean getEncrypted() {
        return encrypted;
    }

    public void setEncrypted(Boolean encrypted) {
        this.encrypted = encrypted;
    }
}
