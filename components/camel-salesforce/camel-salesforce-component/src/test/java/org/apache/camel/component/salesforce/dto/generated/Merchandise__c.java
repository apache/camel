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
import com.thoughtworks.xstream.annotations.XStreamAlias;
import org.apache.camel.component.salesforce.api.dto.AbstractDescribedSObjectBase;
import org.apache.camel.component.salesforce.api.dto.SObjectDescription;
import org.apache.camel.component.salesforce.api.dto.SObjectDescriptionUrls;
import org.apache.camel.component.salesforce.api.dto.SObjectField;

//CHECKSTYLE:OFF
@XStreamAlias("Merchandise__c")
public class Merchandise__c extends AbstractDescribedSObjectBase {

    public Merchandise__c() {
        getAttributes().setType("Merchandise__c");
    }

    private static final SObjectDescription DESCRIPTION = createSObjectDescription();

    private String Description__c;

    @JsonProperty("Description__c")
    public String getDescription__c() {
        return this.Description__c;
    }

    @JsonProperty("Description__c")
    public void setDescription__c(String Description__c) {
        this.Description__c = Description__c;
    }

    private Double Price__c;

    @JsonProperty("Price__c")
    public Double getPrice__c() {
        return this.Price__c;
    }

    @JsonProperty("Price__c")
    public void setPrice__c(Double Price__c) {
        this.Price__c = Price__c;
    }

    private Double Total_Inventory__c;

    @JsonProperty("Total_Inventory__c")
    public Double getTotal_Inventory__c() {
        return this.Total_Inventory__c;
    }

    @JsonProperty("Total_Inventory__c")
    public void setTotal_Inventory__c(Double Total_Inventory__c) {
        this.Total_Inventory__c = Total_Inventory__c;
    }

    private QueryRecordsLine_Item__c Line_Items__r;

    @JsonProperty("Line_Items__r")
    public QueryRecordsLine_Item__c getLine_Items__r() {
        return Line_Items__r;
    }

    @JsonProperty("Line_Items__r")
    public void setLine_Items__r(QueryRecordsLine_Item__c Line_Items__r) {
        this.Line_Items__r = Line_Items__r;
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
        final SObjectField sObjectField4 = createField("Name", "Merchandise Name", "string", "xsd:string", 80, false, true, true, false, false, false, true);
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
        final SObjectField sObjectField10 = createField("LastActivityDate", "Last Activity Date", "date", "xsd:date", 0, false, true, false, false, false, false, false);
        fields1.add(sObjectField10);
        final SObjectField sObjectField11 = createField("LastViewedDate", "Last Viewed Date", "datetime", "xsd:dateTime", 0, false, true, false, false, false, false, false);
        fields1.add(sObjectField11);
        final SObjectField sObjectField12 = createField("LastReferencedDate", "Last Referenced Date", "datetime", "xsd:dateTime", 0, false, true, false, false, false, false,
                                                        false);
        fields1.add(sObjectField12);
        final SObjectField sObjectField13 = createField("Description__c", "Description", "string", "xsd:string", 100, false, true, false, false, true, false, false);
        fields1.add(sObjectField13);
        final SObjectField sObjectField14 = createField("Price__c", "Price", "currency", "xsd:double", 0, false, false, false, false, true, false, false);
        fields1.add(sObjectField14);
        final SObjectField sObjectField15 = createField("Total_Inventory__c", "Total_Inventory", "double", "xsd:double", 0, false, false, false, false, true, false, false);
        fields1.add(sObjectField15);

        description.setLabel("Merchandise");
        description.setLabelPlural("Merchandise");
        description.setName("Merchandise__c");

        final SObjectDescriptionUrls sObjectDescriptionUrls1 = new SObjectDescriptionUrls();
        sObjectDescriptionUrls1.setApprovalLayouts("/services/data/v45.0/sobjects/Merchandise__c/describe/approvalLayouts");
        sObjectDescriptionUrls1.setCompactLayouts("/services/data/v45.0/sobjects/Merchandise__c/describe/compactLayouts");
        sObjectDescriptionUrls1.setDefaultValues("/services/data/v45.0/sobjects/Merchandise__c/defaultValues?recordTypeId&fields");
        sObjectDescriptionUrls1.setDescribe("/services/data/v45.0/sobjects/Merchandise__c/describe");
        sObjectDescriptionUrls1.setLayouts("/services/data/v45.0/sobjects/Merchandise__c/describe/layouts");
        sObjectDescriptionUrls1.setQuickActions("/services/data/v45.0/sobjects/Merchandise__c/quickActions");
        sObjectDescriptionUrls1.setRowTemplate("/services/data/v45.0/sobjects/Merchandise__c/{ID}");
        sObjectDescriptionUrls1.setSobject("/services/data/v45.0/sobjects/Merchandise__c");
        sObjectDescriptionUrls1.setUiDetailTemplate("https://customer-flow-8168-dev-ed.cs42.my.salesforce.com/{ID}");
        sObjectDescriptionUrls1.setUiEditTemplate("https://customer-flow-8168-dev-ed.cs42.my.salesforce.com/{ID}/e");
        sObjectDescriptionUrls1.setUiNewRecord("https://customer-flow-8168-dev-ed.cs42.my.salesforce.com/a02/e");
        description.setUrls(sObjectDescriptionUrls1);

        return description;
    }
}

//CHECKSTYLE:ON
