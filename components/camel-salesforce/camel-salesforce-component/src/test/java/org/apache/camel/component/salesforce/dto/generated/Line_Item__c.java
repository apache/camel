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
package org.apache.camel.component.salesforce.dto.generated;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonTypeResolver;
import org.apache.camel.component.salesforce.api.dto.AbstractDescribedSObjectBase;
import org.apache.camel.component.salesforce.api.dto.AbstractSObjectBase;
import org.apache.camel.component.salesforce.api.dto.SObjectDescription;
import org.apache.camel.component.salesforce.api.dto.SObjectDescriptionUrls;
import org.apache.camel.component.salesforce.api.dto.SObjectField;
import org.apache.camel.component.salesforce.api.utils.AsNestedPropertyResolver;

public class Line_Item__c extends AbstractDescribedSObjectBase {

    @JsonTypeResolver(AsNestedPropertyResolver.class)
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "attributes.type",
        defaultImpl = AbstractDescribedSObjectBase.class)
    @JsonSubTypes({
            @JsonSubTypes.Type(value = User.class, name = "User")
    })
    private AbstractSObjectBase Owner;

    @JsonProperty("Owner")
    public AbstractSObjectBase getOwner() {
        return Owner;
    }

    @JsonProperty("Owner")
    public void setOwner(AbstractSObjectBase owner) {
        Owner = owner;
    }

    public Line_Item__c() {
        getAttributes().setType("Line_Item__c");
    }

    private static final SObjectDescription DESCRIPTION = createSObjectDescription();

    private String Merchandise__c;

    @JsonProperty("Merchandise__c")
    public String getMerchandise__c() {
        return this.Merchandise__c;
    }

    @JsonProperty("Merchandise__c")
    public void setMerchandise__c(String Merchandise__c) {
        this.Merchandise__c = Merchandise__c;
    }

    private Double Unit_Price__c;

    @JsonProperty("Unit_Price__c")
    public Double getUnit_Price__c() {
        return this.Unit_Price__c;
    }

    @JsonProperty("Unit_Price__c")
    public void setUnit_Price__c(Double Unit_Price__c) {
        this.Unit_Price__c = Unit_Price__c;
    }

    private Double Units_Sold__c;

    @JsonProperty("Units_Sold__c")
    public Double getUnits_Sold__c() {
        return this.Units_Sold__c;
    }

    @JsonProperty("Units_Sold__c")
    public void setUnits_Sold__c(Double Units_Sold__c) {
        this.Units_Sold__c = Units_Sold__c;
    }

    private RecordType RecordType;

    @JsonProperty("RecordType")
    public RecordType getRecordType() {
        return this.RecordType;
    }

    @JsonProperty("RecordType")
    public void setRecordType(RecordType RecordType) {
        this.RecordType = RecordType;
    }


    @Override
    public final SObjectDescription description() {
        return DESCRIPTION;
    }

    private static SObjectDescription createSObjectDescription() {
        final SObjectDescription description = new SObjectDescription();

        final List<SObjectField> fields1 = new ArrayList<>();
        description.setFields(fields1);

        final SObjectField sObjectField1 = createField("Id", "Record ID", "id", "tns:ID", 18, false, false, false, false, false, false, true);
        fields1.add(sObjectField1);
        final SObjectField sObjectField2 = createField("OwnerId", "Owner ID", "reference", "tns:ID", 18, false, false, false, false, false, false, false);
        fields1.add(sObjectField2);
        final SObjectField sObjectField3 = createField("IsDeleted", "Deleted", "boolean", "xsd:boolean", 0, false, false, false, false, false, false, false);
        fields1.add(sObjectField3);
        final SObjectField sObjectField4 = createField("Name", "Line Item Number", "string", "xsd:string", 80, false, true, true, false, false, false, true);
        fields1.add(sObjectField4);
        final SObjectField sObjectField5 = createField("CreatedDate", "Created Date", "datetime", "xsd:dateTime", 0, false, false, false, false, false, false, false);
        fields1.add(sObjectField5);
        final SObjectField sObjectField6 = createField("CreatedById", "Created By ID", "reference", "tns:ID", 18, false, false, false, false, false, false, false);
        fields1.add(sObjectField6);
        final SObjectField sObjectField7 = createField("LastModifiedDate", "Last Modified Date", "datetime", "xsd:dateTime", 0, false, false, false, false, false, false, false);
        fields1.add(sObjectField7);
        final SObjectField sObjectField8 = createField("LastModifiedById", "Last Modified By ID", "reference", "tns:ID", 18, false, false, false, false, false, false, false);
        fields1.add(sObjectField8);
        final SObjectField sObjectField9 = createField("SystemModstamp", "System Modstamp", "datetime", "xsd:dateTime", 0, false, false, false, false, false, false, false);
        fields1.add(sObjectField9);
        final SObjectField sObjectField10 = createField("Merchandise__c", "Merchandise", "reference", "tns:ID", 18, false, true, false, false, true, false, false);
        fields1.add(sObjectField10);
        final SObjectField sObjectField11 = createField("Unit_Price__c", "Unit Price", "currency", "xsd:double", 0, false, true, false, false, true, false, false);
        fields1.add(sObjectField11);
        final SObjectField sObjectField12 = createField("Units_Sold__c", "Units Sold", "double", "xsd:double", 0, false, true, false, false, true, false, false);
        fields1.add(sObjectField12);

        description.setLabel("Line Item");
        description.setLabelPlural("Line Items");
        description.setName("Line_Item__c");

        final SObjectDescriptionUrls sObjectDescriptionUrls1 = new SObjectDescriptionUrls();
        sObjectDescriptionUrls1.setApprovalLayouts("/services/data/v45.0/sobjects/Line_Item__c/describe/approvalLayouts");
        sObjectDescriptionUrls1.setCompactLayouts("/services/data/v45.0/sobjects/Line_Item__c/describe/compactLayouts");
        sObjectDescriptionUrls1.setDefaultValues("/services/data/v45.0/sobjects/Line_Item__c/defaultValues?recordTypeId&fields");
        sObjectDescriptionUrls1.setDescribe("/services/data/v45.0/sobjects/Line_Item__c/describe");
        sObjectDescriptionUrls1.setLayouts("/services/data/v45.0/sobjects/Line_Item__c/describe/layouts");
        sObjectDescriptionUrls1.setQuickActions("/services/data/v45.0/sobjects/Line_Item__c/quickActions");
        sObjectDescriptionUrls1.setRowTemplate("/services/data/v45.0/sobjects/Line_Item__c/{ID}");
        sObjectDescriptionUrls1.setSobject("/services/data/v45.0/sobjects/Line_Item__c");
        sObjectDescriptionUrls1.setUiDetailTemplate("https://customer-flow-8168-dev-ed.cs42.my.salesforce.com/{ID}");
        sObjectDescriptionUrls1.setUiEditTemplate("https://customer-flow-8168-dev-ed.cs42.my.salesforce.com/{ID}/e");
        sObjectDescriptionUrls1.setUiNewRecord("https://customer-flow-8168-dev-ed.cs42.my.salesforce.com/a01/e");
        description.setUrls(sObjectDescriptionUrls1);

        return description;
    }
}
