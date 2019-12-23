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
import com.thoughtworks.xstream.annotations.XStreamConverter;
import org.apache.camel.component.salesforce.api.PicklistEnumConverter;
import org.apache.camel.component.salesforce.api.dto.AbstractDescribedSObjectBase;
import org.apache.camel.component.salesforce.api.dto.SObjectDescription;
import org.apache.camel.component.salesforce.api.dto.SObjectDescriptionUrls;
import org.apache.camel.component.salesforce.api.dto.SObjectField;

//CHECKSTYLE:OFF
/**
 * Salesforce DTO for SObject Account
 */
@XStreamAlias("Account")
public class Account extends AbstractDescribedSObjectBase {

    public Account() {
        getAttributes().setType("Account");
    }

    private static final SObjectDescription DESCRIPTION = createSObjectDescription();

    private String MasterRecordId;

    @JsonProperty("MasterRecordId")
    public String getMasterRecordId() {
        return this.MasterRecordId;
    }

    @JsonProperty("MasterRecordId")
    public void setMasterRecordId(String MasterRecordId) {
        this.MasterRecordId = MasterRecordId;
    }

    @XStreamAlias("MasterRecord")
    private Account MasterRecord;

    @JsonProperty("MasterRecord")
    public Account getMasterRecord() {
        return this.MasterRecord;
    }

    private String ParentId;

    @JsonProperty("ParentId")
    public String getParentId() {
        return this.ParentId;
    }

    @JsonProperty("ParentId")
    public void setParentId(String ParentId) {
        this.ParentId = ParentId;
    }

    @XStreamAlias("Parent")
    private Account Parent;

    @JsonProperty("Parent")
    public Account getParent() {
        return this.Parent;
    }

    @JsonProperty("Parent")
    public void setParent(Account Parent) {
        this.Parent = Parent;
    }

    private String BillingStreet;

    @JsonProperty("BillingStreet")
    public String getBillingStreet() {
        return this.BillingStreet;
    }

    @JsonProperty("BillingStreet")
    public void setBillingStreet(String BillingStreet) {
        this.BillingStreet = BillingStreet;
    }

    private String BillingCity;

    @JsonProperty("BillingCity")
    public String getBillingCity() {
        return this.BillingCity;
    }

    @JsonProperty("BillingCity")
    public void setBillingCity(String BillingCity) {
        this.BillingCity = BillingCity;
    }

    private String BillingState;

    @JsonProperty("BillingState")
    public String getBillingState() {
        return this.BillingState;
    }

    @JsonProperty("BillingState")
    public void setBillingState(String BillingState) {
        this.BillingState = BillingState;
    }

    private String BillingPostalCode;

    @JsonProperty("BillingPostalCode")
    public String getBillingPostalCode() {
        return this.BillingPostalCode;
    }

    @JsonProperty("BillingPostalCode")
    public void setBillingPostalCode(String BillingPostalCode) {
        this.BillingPostalCode = BillingPostalCode;
    }

    private String BillingCountry;

    @JsonProperty("BillingCountry")
    public String getBillingCountry() {
        return this.BillingCountry;
    }

    @JsonProperty("BillingCountry")
    public void setBillingCountry(String BillingCountry) {
        this.BillingCountry = BillingCountry;
    }

    private Double BillingLatitude;

    @JsonProperty("BillingLatitude")
    public Double getBillingLatitude() {
        return this.BillingLatitude;
    }

    @JsonProperty("BillingLatitude")
    public void setBillingLatitude(Double BillingLatitude) {
        this.BillingLatitude = BillingLatitude;
    }

    private Double BillingLongitude;

    @JsonProperty("BillingLongitude")
    public Double getBillingLongitude() {
        return this.BillingLongitude;
    }

    @JsonProperty("BillingLongitude")
    public void setBillingLongitude(Double BillingLongitude) {
        this.BillingLongitude = BillingLongitude;
    }

    private org.apache.camel.component.salesforce.api.dto.Address BillingAddress;

    @JsonProperty("BillingAddress")
    public org.apache.camel.component.salesforce.api.dto.Address getBillingAddress() {
        return this.BillingAddress;
    }

    @JsonProperty("BillingAddress")
    public void setBillingAddress(org.apache.camel.component.salesforce.api.dto.Address BillingAddress) {
        this.BillingAddress = BillingAddress;
    }

    private String ShippingStreet;

    @JsonProperty("ShippingStreet")
    public String getShippingStreet() {
        return this.ShippingStreet;
    }

    @JsonProperty("ShippingStreet")
    public void setShippingStreet(String ShippingStreet) {
        this.ShippingStreet = ShippingStreet;
    }

    private String ShippingCity;

    @JsonProperty("ShippingCity")
    public String getShippingCity() {
        return this.ShippingCity;
    }

    @JsonProperty("ShippingCity")
    public void setShippingCity(String ShippingCity) {
        this.ShippingCity = ShippingCity;
    }

    private String ShippingState;

    @JsonProperty("ShippingState")
    public String getShippingState() {
        return this.ShippingState;
    }

    @JsonProperty("ShippingState")
    public void setShippingState(String ShippingState) {
        this.ShippingState = ShippingState;
    }

    private String ShippingPostalCode;

    @JsonProperty("ShippingPostalCode")
    public String getShippingPostalCode() {
        return this.ShippingPostalCode;
    }

    @JsonProperty("ShippingPostalCode")
    public void setShippingPostalCode(String ShippingPostalCode) {
        this.ShippingPostalCode = ShippingPostalCode;
    }

    private String ShippingCountry;

    @JsonProperty("ShippingCountry")
    public String getShippingCountry() {
        return this.ShippingCountry;
    }

    @JsonProperty("ShippingCountry")
    public void setShippingCountry(String ShippingCountry) {
        this.ShippingCountry = ShippingCountry;
    }

    private Double ShippingLatitude;

    @JsonProperty("ShippingLatitude")
    public Double getShippingLatitude() {
        return this.ShippingLatitude;
    }

    @JsonProperty("ShippingLatitude")
    public void setShippingLatitude(Double ShippingLatitude) {
        this.ShippingLatitude = ShippingLatitude;
    }

    private Double ShippingLongitude;

    @JsonProperty("ShippingLongitude")
    public Double getShippingLongitude() {
        return this.ShippingLongitude;
    }

    @JsonProperty("ShippingLongitude")
    public void setShippingLongitude(Double ShippingLongitude) {
        this.ShippingLongitude = ShippingLongitude;
    }

    private org.apache.camel.component.salesforce.api.dto.Address ShippingAddress;

    @JsonProperty("ShippingAddress")
    public org.apache.camel.component.salesforce.api.dto.Address getShippingAddress() {
        return this.ShippingAddress;
    }

    @JsonProperty("ShippingAddress")
    public void setShippingAddress(org.apache.camel.component.salesforce.api.dto.Address ShippingAddress) {
        this.ShippingAddress = ShippingAddress;
    }

    private String Phone;

    @JsonProperty("Phone")
    public String getPhone() {
        return this.Phone;
    }

    @JsonProperty("Phone")
    public void setPhone(String Phone) {
        this.Phone = Phone;
    }

    private String Fax;

    @JsonProperty("Fax")
    public String getFax() {
        return this.Fax;
    }

    @JsonProperty("Fax")
    public void setFax(String Fax) {
        this.Fax = Fax;
    }

    private String AccountNumber;

    @JsonProperty("AccountNumber")
    public String getAccountNumber() {
        return this.AccountNumber;
    }

    @JsonProperty("AccountNumber")
    public void setAccountNumber(String AccountNumber) {
        this.AccountNumber = AccountNumber;
    }

    private String Website;

    @JsonProperty("Website")
    public String getWebsite() {
        return this.Website;
    }

    @JsonProperty("Website")
    public void setWebsite(String Website) {
        this.Website = Website;
    }

    private String PhotoUrl;

    @JsonProperty("PhotoUrl")
    public String getPhotoUrl() {
        return this.PhotoUrl;
    }

    @JsonProperty("PhotoUrl")
    public void setPhotoUrl(String PhotoUrl) {
        this.PhotoUrl = PhotoUrl;
    }

    private String Sic;

    @JsonProperty("Sic")
    public String getSic() {
        return this.Sic;
    }

    @JsonProperty("Sic")
    public void setSic(String Sic) {
        this.Sic = Sic;
    }

    @XStreamConverter(PicklistEnumConverter.class)
    private Account_IndustryEnum Industry;

    @JsonProperty("Industry")
    public Account_IndustryEnum getIndustry() {
        return this.Industry;
    }

    @JsonProperty("Industry")
    public void setIndustry(Account_IndustryEnum Industry) {
        this.Industry = Industry;
    }

    private Double AnnualRevenue;

    @JsonProperty("AnnualRevenue")
    public Double getAnnualRevenue() {
        return this.AnnualRevenue;
    }

    @JsonProperty("AnnualRevenue")
    public void setAnnualRevenue(Double AnnualRevenue) {
        this.AnnualRevenue = AnnualRevenue;
    }

    private Integer NumberOfEmployees;

    @JsonProperty("NumberOfEmployees")
    public Integer getNumberOfEmployees() {
        return this.NumberOfEmployees;
    }

    @JsonProperty("NumberOfEmployees")
    public void setNumberOfEmployees(Integer NumberOfEmployees) {
        this.NumberOfEmployees = NumberOfEmployees;
    }

    private String TickerSymbol;

    @JsonProperty("TickerSymbol")
    public String getTickerSymbol() {
        return this.TickerSymbol;
    }

    @JsonProperty("TickerSymbol")
    public void setTickerSymbol(String TickerSymbol) {
        this.TickerSymbol = TickerSymbol;
    }

    private String Description;

    @JsonProperty("Description")
    public String getDescription() {
        return this.Description;
    }

    @JsonProperty("Description")
    public void setDescription(String Description) {
        this.Description = Description;
    }

    private String Site;

    @JsonProperty("Site")
    public String getSite() {
        return this.Site;
    }

    @JsonProperty("Site")
    public void setSite(String Site) {
        this.Site = Site;
    }

    private String Jigsaw;

    @JsonProperty("Jigsaw")
    public String getJigsaw() {
        return this.Jigsaw;
    }

    @JsonProperty("Jigsaw")
    public void setJigsaw(String Jigsaw) {
        this.Jigsaw = Jigsaw;
    }

    private String JigsawCompanyId;

    @JsonProperty("JigsawCompanyId")
    public String getJigsawCompanyId() {
        return this.JigsawCompanyId;
    }

    @JsonProperty("JigsawCompanyId")
    public void setJigsawCompanyId(String JigsawCompanyId) {
        this.JigsawCompanyId = JigsawCompanyId;
    }

    private String DunsNumber;

    @JsonProperty("DunsNumber")
    public String getDunsNumber() {
        return this.DunsNumber;
    }

    @JsonProperty("DunsNumber")
    public void setDunsNumber(String DunsNumber) {
        this.DunsNumber = DunsNumber;
    }

    private String Tradestyle;

    @JsonProperty("Tradestyle")
    public String getTradestyle() {
        return this.Tradestyle;
    }

    @JsonProperty("Tradestyle")
    public void setTradestyle(String Tradestyle) {
        this.Tradestyle = Tradestyle;
    }

    private String NaicsCode;

    @JsonProperty("NaicsCode")
    public String getNaicsCode() {
        return this.NaicsCode;
    }

    @JsonProperty("NaicsCode")
    public void setNaicsCode(String NaicsCode) {
        this.NaicsCode = NaicsCode;
    }

    private String NaicsDesc;

    @JsonProperty("NaicsDesc")
    public String getNaicsDesc() {
        return this.NaicsDesc;
    }

    @JsonProperty("NaicsDesc")
    public void setNaicsDesc(String NaicsDesc) {
        this.NaicsDesc = NaicsDesc;
    }

    private String YearStarted;

    @JsonProperty("YearStarted")
    public String getYearStarted() {
        return this.YearStarted;
    }

    @JsonProperty("YearStarted")
    public void setYearStarted(String YearStarted) {
        this.YearStarted = YearStarted;
    }

    private String SicDesc;

    @JsonProperty("SicDesc")
    public String getSicDesc() {
        return this.SicDesc;
    }

    @JsonProperty("SicDesc")
    public void setSicDesc(String SicDesc) {
        this.SicDesc = SicDesc;
    }

    private String DandbCompanyId;

    @JsonProperty("DandbCompanyId")
    public String getDandbCompanyId() {
        return this.DandbCompanyId;
    }

    @JsonProperty("DandbCompanyId")
    public void setDandbCompanyId(String DandbCompanyId) {
        this.DandbCompanyId = DandbCompanyId;
    }

    private String OperatingHoursId;

    @JsonProperty("OperatingHoursId")
    public String getOperatingHoursId() {
        return this.OperatingHoursId;
    }

    @JsonProperty("OperatingHoursId")
    public void setOperatingHoursId(String OperatingHoursId) {
        this.OperatingHoursId = OperatingHoursId;
    }

    private Double Shipping_Location__Latitude__s;

    @JsonProperty("Shipping_Location__Latitude__s")
    public Double getShipping_Location__Latitude__s() {
        return this.Shipping_Location__Latitude__s;
    }

    @JsonProperty("Shipping_Location__Latitude__s")
    public void setShipping_Location__Latitude__s(Double Shipping_Location__Latitude__s) {
        this.Shipping_Location__Latitude__s = Shipping_Location__Latitude__s;
    }

    private Double Shipping_Location__Longitude__s;

    @JsonProperty("Shipping_Location__Longitude__s")
    public Double getShipping_Location__Longitude__s() {
        return this.Shipping_Location__Longitude__s;
    }

    @JsonProperty("Shipping_Location__Longitude__s")
    public void setShipping_Location__Longitude__s(Double Shipping_Location__Longitude__s) {
        this.Shipping_Location__Longitude__s = Shipping_Location__Longitude__s;
    }

    private org.apache.camel.component.salesforce.api.dto.GeoLocation Shipping_Location__c;

    @JsonProperty("Shipping_Location__c")
    public org.apache.camel.component.salesforce.api.dto.GeoLocation getShipping_Location__c() {
        return this.Shipping_Location__c;
    }

    @JsonProperty("Shipping_Location__c")
    public void setShipping_Location__c(org.apache.camel.component.salesforce.api.dto.GeoLocation Shipping_Location__c) {
        this.Shipping_Location__c = Shipping_Location__c;
    }

    private String External_Id__c;

    @JsonProperty("External_Id__c")
    public String getExternal_Id__c() {
        return this.External_Id__c;
    }

    @JsonProperty("External_Id__c")
    public void setExternal_Id__c(String External_Id__c) {
        this.External_Id__c = External_Id__c;
    }

    private QueryRecordsAccount ChildAccounts;

    @JsonProperty("ChildAccounts")
    public QueryRecordsAccount getChildAccounts() {
        return ChildAccounts;
    }

    @JsonProperty("ChildAccounts")
    public void setChildAccounts(QueryRecordsAccount ChildAccounts) {
        this.ChildAccounts = ChildAccounts;
    }

    private QueryRecordsContact Contacts;

    @JsonProperty("Contacts")
    public QueryRecordsContact getContacts() {
        return Contacts;
    }

    @JsonProperty("Contacts")
    public void setContacts(QueryRecordsContact Contacts) {
        this.Contacts = Contacts;
    }

    @Override
    public final SObjectDescription description() {
        return DESCRIPTION;
    }

    private static SObjectDescription createSObjectDescription() {
        final SObjectDescription description = new SObjectDescription();

        final List<SObjectField> fields1 = new ArrayList<>();
        description.setFields(fields1);

        final SObjectField sObjectField1 = createField("Id", "Account ID", "id", "tns:ID", 18, false, false, false, false, false, false, true);
        fields1.add(sObjectField1);
        final SObjectField sObjectField2 = createField("IsDeleted", "Deleted", "boolean", "xsd:boolean", 0, false, false, false, false, false, false, false);
        fields1.add(sObjectField2);
        final SObjectField sObjectField3 = createField("MasterRecordId", "Master Record ID", "reference", "tns:ID", 18, false, true, false, false, false, false, false);
        fields1.add(sObjectField3);
        final SObjectField sObjectField4 = createField("Name", "Account Name", "string", "xsd:string", 255, false, false, true, false, false, false, false);
        fields1.add(sObjectField4);
        final SObjectField sObjectField5 = createField("Type", "Account Type", "picklist", "xsd:string", 40, false, true, false, false, false, false, false);
        fields1.add(sObjectField5);
        final SObjectField sObjectField6 = createField("ParentId", "Parent Account ID", "reference", "tns:ID", 18, false, true, false, false, false, false, false);
        fields1.add(sObjectField6);
        final SObjectField sObjectField7 = createField("BillingStreet", "Billing Street", "textarea", "xsd:string", 255, false, true, false, false, false, false, false);
        fields1.add(sObjectField7);
        final SObjectField sObjectField8 = createField("BillingCity", "Billing City", "string", "xsd:string", 40, false, true, false, false, false, false, false);
        fields1.add(sObjectField8);
        final SObjectField sObjectField9 = createField("BillingState", "Billing State/Province", "string", "xsd:string", 80, false, true, false, false, false, false, false);
        fields1.add(sObjectField9);
        final SObjectField sObjectField10 = createField("BillingPostalCode", "Billing Zip/Postal Code", "string", "xsd:string", 20, false, true, false, false, false, false, false);
        fields1.add(sObjectField10);
        final SObjectField sObjectField11 = createField("BillingCountry", "Billing Country", "string", "xsd:string", 80, false, true, false, false, false, false, false);
        fields1.add(sObjectField11);
        final SObjectField sObjectField12 = createField("BillingLatitude", "Billing Latitude", "double", "xsd:double", 0, false, true, false, false, false, false, false);
        fields1.add(sObjectField12);
        final SObjectField sObjectField13 = createField("BillingLongitude", "Billing Longitude", "double", "xsd:double", 0, false, true, false, false, false, false, false);
        fields1.add(sObjectField13);
        final SObjectField sObjectField14 = createField("BillingGeocodeAccuracy", "Billing Geocode Accuracy", "picklist", "xsd:string", 40, false, true, false, false, false, false,
                                                        false);
        fields1.add(sObjectField14);
        final SObjectField sObjectField15 = createField("BillingAddress", "Billing Address", "address", "urn:address", 0, false, true, false, false, false, false, false);
        fields1.add(sObjectField15);
        final SObjectField sObjectField16 = createField("ShippingStreet", "Shipping Street", "textarea", "xsd:string", 255, false, true, false, false, false, false, false);
        fields1.add(sObjectField16);
        final SObjectField sObjectField17 = createField("ShippingCity", "Shipping City", "string", "xsd:string", 40, false, true, false, false, false, false, false);
        fields1.add(sObjectField17);
        final SObjectField sObjectField18 = createField("ShippingState", "Shipping State/Province", "string", "xsd:string", 80, false, true, false, false, false, false, false);
        fields1.add(sObjectField18);
        final SObjectField sObjectField19 = createField("ShippingPostalCode", "Shipping Zip/Postal Code", "string", "xsd:string", 20, false, true, false, false, false, false,
                                                        false);
        fields1.add(sObjectField19);
        final SObjectField sObjectField20 = createField("ShippingCountry", "Shipping Country", "string", "xsd:string", 80, false, true, false, false, false, false, false);
        fields1.add(sObjectField20);
        final SObjectField sObjectField21 = createField("ShippingLatitude", "Shipping Latitude", "double", "xsd:double", 0, false, true, false, false, false, false, false);
        fields1.add(sObjectField21);
        final SObjectField sObjectField22 = createField("ShippingLongitude", "Shipping Longitude", "double", "xsd:double", 0, false, true, false, false, false, false, false);
        fields1.add(sObjectField22);
        final SObjectField sObjectField23 = createField("ShippingGeocodeAccuracy", "Shipping Geocode Accuracy", "picklist", "xsd:string", 40, false, true, false, false, false,
                                                        false, false);
        fields1.add(sObjectField23);
        final SObjectField sObjectField24 = createField("ShippingAddress", "Shipping Address", "address", "urn:address", 0, false, true, false, false, false, false, false);
        fields1.add(sObjectField24);
        final SObjectField sObjectField25 = createField("Phone", "Account Phone", "phone", "xsd:string", 40, false, true, false, false, false, false, false);
        fields1.add(sObjectField25);
        final SObjectField sObjectField26 = createField("Fax", "Account Fax", "phone", "xsd:string", 40, false, true, false, false, false, false, false);
        fields1.add(sObjectField26);
        final SObjectField sObjectField27 = createField("AccountNumber", "Account Number", "string", "xsd:string", 40, false, true, false, false, false, false, false);
        fields1.add(sObjectField27);
        final SObjectField sObjectField28 = createField("Website", "Website", "url", "xsd:string", 255, false, true, false, false, false, false, false);
        fields1.add(sObjectField28);
        final SObjectField sObjectField29 = createField("PhotoUrl", "Photo URL", "url", "xsd:string", 255, false, true, false, false, false, false, false);
        fields1.add(sObjectField29);
        final SObjectField sObjectField30 = createField("Sic", "SIC Code", "string", "xsd:string", 20, false, true, false, false, false, false, false);
        fields1.add(sObjectField30);
        final SObjectField sObjectField31 = createField("Industry", "Industry", "picklist", "xsd:string", 40, false, true, false, false, false, false, false);
        fields1.add(sObjectField31);
        final SObjectField sObjectField32 = createField("AnnualRevenue", "Annual Revenue", "currency", "xsd:double", 0, false, true, false, false, false, false, false);
        fields1.add(sObjectField32);
        final SObjectField sObjectField33 = createField("NumberOfEmployees", "Employees", "int", "xsd:int", 0, false, true, false, false, false, false, false);
        fields1.add(sObjectField33);
        final SObjectField sObjectField34 = createField("Ownership", "Ownership", "picklist", "xsd:string", 40, false, true, false, false, false, false, false);
        fields1.add(sObjectField34);
        final SObjectField sObjectField35 = createField("TickerSymbol", "Ticker Symbol", "string", "xsd:string", 20, false, true, false, false, false, false, false);
        fields1.add(sObjectField35);
        final SObjectField sObjectField36 = createField("Description", "Account Description", "textarea", "xsd:string", 32000, false, true, false, false, false, false, false);
        fields1.add(sObjectField36);
        final SObjectField sObjectField37 = createField("Rating", "Account Rating", "picklist", "xsd:string", 40, false, true, false, false, false, false, false);
        fields1.add(sObjectField37);
        final SObjectField sObjectField38 = createField("Site", "Account Site", "string", "xsd:string", 80, false, true, false, false, false, false, false);
        fields1.add(sObjectField38);
        final SObjectField sObjectField39 = createField("OwnerId", "Owner ID", "reference", "tns:ID", 18, false, false, false, false, false, false, false);
        fields1.add(sObjectField39);
        final SObjectField sObjectField40 = createField("CreatedDate", "Created Date", "datetime", "xsd:dateTime", 0, false, false, false, false, false, false, false);
        fields1.add(sObjectField40);
        final SObjectField sObjectField41 = createField("CreatedById", "Created By ID", "reference", "tns:ID", 18, false, false, false, false, false, false, false);
        fields1.add(sObjectField41);
        final SObjectField sObjectField42 = createField("LastModifiedDate", "Last Modified Date", "datetime", "xsd:dateTime", 0, false, false, false, false, false, false, false);
        fields1.add(sObjectField42);
        final SObjectField sObjectField43 = createField("LastModifiedById", "Last Modified By ID", "reference", "tns:ID", 18, false, false, false, false, false, false, false);
        fields1.add(sObjectField43);
        final SObjectField sObjectField44 = createField("SystemModstamp", "System Modstamp", "datetime", "xsd:dateTime", 0, false, false, false, false, false, false, false);
        fields1.add(sObjectField44);
        final SObjectField sObjectField45 = createField("LastActivityDate", "Last Activity", "date", "xsd:date", 0, false, true, false, false, false, false, false);
        fields1.add(sObjectField45);
        final SObjectField sObjectField46 = createField("LastViewedDate", "Last Viewed Date", "datetime", "xsd:dateTime", 0, false, true, false, false, false, false, false);
        fields1.add(sObjectField46);
        final SObjectField sObjectField47 = createField("LastReferencedDate", "Last Referenced Date", "datetime", "xsd:dateTime", 0, false, true, false, false, false, false,
                                                        false);
        fields1.add(sObjectField47);
        final SObjectField sObjectField48 = createField("Jigsaw", "Data.com Key", "string", "xsd:string", 20, false, true, false, false, false, false, false);
        fields1.add(sObjectField48);
        final SObjectField sObjectField49 = createField("JigsawCompanyId", "Jigsaw Company ID", "string", "xsd:string", 20, false, true, false, false, false, false, false);
        fields1.add(sObjectField49);
        final SObjectField sObjectField50 = createField("CleanStatus", "Clean Status", "picklist", "xsd:string", 40, false, true, false, false, false, false, false);
        fields1.add(sObjectField50);
        final SObjectField sObjectField51 = createField("AccountSource", "Account Source", "picklist", "xsd:string", 40, false, true, false, false, false, false, false);
        fields1.add(sObjectField51);
        final SObjectField sObjectField52 = createField("DunsNumber", "D-U-N-S Number", "string", "xsd:string", 9, false, true, false, false, false, false, false);
        fields1.add(sObjectField52);
        final SObjectField sObjectField53 = createField("Tradestyle", "Tradestyle", "string", "xsd:string", 255, false, true, false, false, false, false, false);
        fields1.add(sObjectField53);
        final SObjectField sObjectField54 = createField("NaicsCode", "NAICS Code", "string", "xsd:string", 8, false, true, false, false, false, false, false);
        fields1.add(sObjectField54);
        final SObjectField sObjectField55 = createField("NaicsDesc", "NAICS Description", "string", "xsd:string", 120, false, true, false, false, false, false, false);
        fields1.add(sObjectField55);
        final SObjectField sObjectField56 = createField("YearStarted", "Year Started", "string", "xsd:string", 4, false, true, false, false, false, false, false);
        fields1.add(sObjectField56);
        final SObjectField sObjectField57 = createField("SicDesc", "SIC Description", "string", "xsd:string", 80, false, true, false, false, false, false, false);
        fields1.add(sObjectField57);
        final SObjectField sObjectField58 = createField("DandbCompanyId", "D&B Company ID", "reference", "tns:ID", 18, false, true, false, false, false, false, false);
        fields1.add(sObjectField58);
        final SObjectField sObjectField59 = createField("OperatingHoursId", "Operating Hour ID", "reference", "tns:ID", 18, false, true, false, false, false, false, false);
        fields1.add(sObjectField59);
        final SObjectField sObjectField60 = createField("Shipping_Location__Latitude__s", "Shipping_Location (Latitude)", "double", "xsd:double", 0, false, true, false, false,
                                                        true, false, false);
        fields1.add(sObjectField60);
        final SObjectField sObjectField61 = createField("Shipping_Location__Longitude__s", "Shipping_Location (Longitude)", "double", "xsd:double", 0, false, true, false, false,
                                                        true, false, false);
        fields1.add(sObjectField61);
        final SObjectField sObjectField62 = createField("Shipping_Location__c", "Shipping_Location", "location", "urn:location", 0, false, true, false, false, true, false, false);
        fields1.add(sObjectField62);
        final SObjectField sObjectField63 = createField("External_Id__c", "External Id", "string", "xsd:string", 255, true, true, false, true, true, false, true);
        fields1.add(sObjectField63);

        description.setLabel("Account");
        description.setLabelPlural("Accounts");
        description.setName("Account");

        final SObjectDescriptionUrls sObjectDescriptionUrls1 = new SObjectDescriptionUrls();
        sObjectDescriptionUrls1.setApprovalLayouts("/services/data/v45.0/sobjects/Account/describe/approvalLayouts");
        sObjectDescriptionUrls1.setCompactLayouts("/services/data/v45.0/sobjects/Account/describe/compactLayouts");
        sObjectDescriptionUrls1.setDefaultValues("/services/data/v45.0/sobjects/Account/defaultValues?recordTypeId&fields");
        sObjectDescriptionUrls1.setDescribe("/services/data/v45.0/sobjects/Account/describe");
        sObjectDescriptionUrls1.setLayouts("/services/data/v45.0/sobjects/Account/describe/layouts");
        sObjectDescriptionUrls1.setListviews("/services/data/v45.0/sobjects/Account/listviews");
        sObjectDescriptionUrls1.setQuickActions("/services/data/v45.0/sobjects/Account/quickActions");
        sObjectDescriptionUrls1.setRowTemplate("/services/data/v45.0/sobjects/Account/{ID}");
        sObjectDescriptionUrls1.setSobject("/services/data/v45.0/sobjects/Account");
        sObjectDescriptionUrls1.setUiDetailTemplate("https://customer-flow-8168-dev-ed.cs42.my.salesforce.com/{ID}");
        sObjectDescriptionUrls1.setUiEditTemplate("https://customer-flow-8168-dev-ed.cs42.my.salesforce.com/{ID}/e");
        sObjectDescriptionUrls1.setUiNewRecord("https://customer-flow-8168-dev-ed.cs42.my.salesforce.com/001/e");
        description.setUrls(sObjectDescriptionUrls1);

        return description;
    }
}
//CHECKSTYLE:ON
