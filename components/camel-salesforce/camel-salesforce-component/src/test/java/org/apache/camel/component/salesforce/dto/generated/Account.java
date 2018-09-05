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
package org.apache.camel.component.salesforce.dto.generated;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamConverter;

import org.apache.camel.component.salesforce.api.PicklistEnumConverter;
import org.apache.camel.component.salesforce.api.dto.AbstractDescribedSObjectBase;
import org.apache.camel.component.salesforce.api.dto.InfoUrls;
import org.apache.camel.component.salesforce.api.dto.RecordTypeInfo;
import org.apache.camel.component.salesforce.api.dto.SObjectDescription;
import org.apache.camel.component.salesforce.api.dto.SObjectDescriptionUrls;
import org.apache.camel.component.salesforce.api.dto.SObjectField;

//CHECKSTYLE:OFF
/**
 * Salesforce DTO for SObject Account
 */
@XStreamAlias("Account")
public class Account extends AbstractDescribedSObjectBase {

    private static final SObjectDescription DESCRIPTION = createSObjectDescription();

    // MasterRecordId
    private String MasterRecordId;

    @JsonProperty("MasterRecordId")
    public String getMasterRecordId() {
        return this.MasterRecordId;
    }

    @JsonProperty("MasterRecordId")
    public void setMasterRecordId(String MasterRecordId) {
        this.MasterRecordId = MasterRecordId;
    }

    // ParentId
    private String ParentId;

    @JsonProperty("ParentId")
    public String getParentId() {
        return this.ParentId;
    }

    @JsonProperty("ParentId")
    public void setParentId(String ParentId) {
        this.ParentId = ParentId;
    }

    // BillingStreet
    private String BillingStreet;

    @JsonProperty("BillingStreet")
    public String getBillingStreet() {
        return this.BillingStreet;
    }

    @JsonProperty("BillingStreet")
    public void setBillingStreet(String BillingStreet) {
        this.BillingStreet = BillingStreet;
    }

    // BillingCity
    private String BillingCity;

    @JsonProperty("BillingCity")
    public String getBillingCity() {
        return this.BillingCity;
    }

    @JsonProperty("BillingCity")
    public void setBillingCity(String BillingCity) {
        this.BillingCity = BillingCity;
    }

    // BillingState
    private String BillingState;

    @JsonProperty("BillingState")
    public String getBillingState() {
        return this.BillingState;
    }

    @JsonProperty("BillingState")
    public void setBillingState(String BillingState) {
        this.BillingState = BillingState;
    }

    // BillingPostalCode
    private String BillingPostalCode;

    @JsonProperty("BillingPostalCode")
    public String getBillingPostalCode() {
        return this.BillingPostalCode;
    }

    @JsonProperty("BillingPostalCode")
    public void setBillingPostalCode(String BillingPostalCode) {
        this.BillingPostalCode = BillingPostalCode;
    }

    // BillingCountry
    private String BillingCountry;

    @JsonProperty("BillingCountry")
    public String getBillingCountry() {
        return this.BillingCountry;
    }

    @JsonProperty("BillingCountry")
    public void setBillingCountry(String BillingCountry) {
        this.BillingCountry = BillingCountry;
    }

    // BillingLatitude
    private Double BillingLatitude;

    @JsonProperty("BillingLatitude")
    public Double getBillingLatitude() {
        return this.BillingLatitude;
    }

    @JsonProperty("BillingLatitude")
    public void setBillingLatitude(Double BillingLatitude) {
        this.BillingLatitude = BillingLatitude;
    }

    // BillingLongitude
    private Double BillingLongitude;

    @JsonProperty("BillingLongitude")
    public Double getBillingLongitude() {
        return this.BillingLongitude;
    }

    @JsonProperty("BillingLongitude")
    public void setBillingLongitude(Double BillingLongitude) {
        this.BillingLongitude = BillingLongitude;
    }

    // BillingAddress
    private org.apache.camel.component.salesforce.api.dto.Address BillingAddress;

    @JsonProperty("BillingAddress")
    public org.apache.camel.component.salesforce.api.dto.Address getBillingAddress() {
        return this.BillingAddress;
    }

    @JsonProperty("BillingAddress")
    public void setBillingAddress(org.apache.camel.component.salesforce.api.dto.Address BillingAddress) {
        this.BillingAddress = BillingAddress;
    }

    // ShippingStreet
    private String ShippingStreet;

    @JsonProperty("ShippingStreet")
    public String getShippingStreet() {
        return this.ShippingStreet;
    }

    @JsonProperty("ShippingStreet")
    public void setShippingStreet(String ShippingStreet) {
        this.ShippingStreet = ShippingStreet;
    }

    // ShippingCity
    private String ShippingCity;

    @JsonProperty("ShippingCity")
    public String getShippingCity() {
        return this.ShippingCity;
    }

    @JsonProperty("ShippingCity")
    public void setShippingCity(String ShippingCity) {
        this.ShippingCity = ShippingCity;
    }

    // ShippingState
    private String ShippingState;

    @JsonProperty("ShippingState")
    public String getShippingState() {
        return this.ShippingState;
    }

    @JsonProperty("ShippingState")
    public void setShippingState(String ShippingState) {
        this.ShippingState = ShippingState;
    }

    // ShippingPostalCode
    private String ShippingPostalCode;

    @JsonProperty("ShippingPostalCode")
    public String getShippingPostalCode() {
        return this.ShippingPostalCode;
    }

    @JsonProperty("ShippingPostalCode")
    public void setShippingPostalCode(String ShippingPostalCode) {
        this.ShippingPostalCode = ShippingPostalCode;
    }

    // ShippingCountry
    private String ShippingCountry;

    @JsonProperty("ShippingCountry")
    public String getShippingCountry() {
        return this.ShippingCountry;
    }

    @JsonProperty("ShippingCountry")
    public void setShippingCountry(String ShippingCountry) {
        this.ShippingCountry = ShippingCountry;
    }

    // ShippingLatitude
    private Double ShippingLatitude;

    @JsonProperty("ShippingLatitude")
    public Double getShippingLatitude() {
        return this.ShippingLatitude;
    }

    @JsonProperty("ShippingLatitude")
    public void setShippingLatitude(Double ShippingLatitude) {
        this.ShippingLatitude = ShippingLatitude;
    }

    // ShippingLongitude
    private Double ShippingLongitude;

    @JsonProperty("ShippingLongitude")
    public Double getShippingLongitude() {
        return this.ShippingLongitude;
    }

    @JsonProperty("ShippingLongitude")
    public void setShippingLongitude(Double ShippingLongitude) {
        this.ShippingLongitude = ShippingLongitude;
    }

    // ShippingAddress
    private org.apache.camel.component.salesforce.api.dto.Address ShippingAddress;

    @JsonProperty("ShippingAddress")
    public org.apache.camel.component.salesforce.api.dto.Address getShippingAddress() {
        return this.ShippingAddress;
    }

    @JsonProperty("ShippingAddress")
    public void setShippingAddress(org.apache.camel.component.salesforce.api.dto.Address ShippingAddress) {
        this.ShippingAddress = ShippingAddress;
    }

    // Phone
    private String Phone;

    @JsonProperty("Phone")
    public String getPhone() {
        return this.Phone;
    }

    @JsonProperty("Phone")
    public void setPhone(String Phone) {
        this.Phone = Phone;
    }

    // Fax
    private String Fax;

    @JsonProperty("Fax")
    public String getFax() {
        return this.Fax;
    }

    @JsonProperty("Fax")
    public void setFax(String Fax) {
        this.Fax = Fax;
    }

    // AccountNumber
    private String AccountNumber;

    @JsonProperty("AccountNumber")
    public String getAccountNumber() {
        return this.AccountNumber;
    }

    @JsonProperty("AccountNumber")
    public void setAccountNumber(String AccountNumber) {
        this.AccountNumber = AccountNumber;
    }

    // Website
    private String Website;

    @JsonProperty("Website")
    public String getWebsite() {
        return this.Website;
    }

    @JsonProperty("Website")
    public void setWebsite(String Website) {
        this.Website = Website;
    }

    // PhotoUrl
    private String PhotoUrl;

    @JsonProperty("PhotoUrl")
    public String getPhotoUrl() {
        return this.PhotoUrl;
    }

    @JsonProperty("PhotoUrl")
    public void setPhotoUrl(String PhotoUrl) {
        this.PhotoUrl = PhotoUrl;
    }

    // Sic
    private String Sic;

    @JsonProperty("Sic")
    public String getSic() {
        return this.Sic;
    }

    @JsonProperty("Sic")
    public void setSic(String Sic) {
        this.Sic = Sic;
    }

    // Industry
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

    // AnnualRevenue
    private Double AnnualRevenue;

    @JsonProperty("AnnualRevenue")
    public Double getAnnualRevenue() {
        return this.AnnualRevenue;
    }

    @JsonProperty("AnnualRevenue")
    public void setAnnualRevenue(Double AnnualRevenue) {
        this.AnnualRevenue = AnnualRevenue;
    }

    // NumberOfEmployees
    private Integer NumberOfEmployees;

    @JsonProperty("NumberOfEmployees")
    public Integer getNumberOfEmployees() {
        return this.NumberOfEmployees;
    }

    @JsonProperty("NumberOfEmployees")
    public void setNumberOfEmployees(Integer NumberOfEmployees) {
        this.NumberOfEmployees = NumberOfEmployees;
    }

    // TickerSymbol
    private String TickerSymbol;

    @JsonProperty("TickerSymbol")
    public String getTickerSymbol() {
        return this.TickerSymbol;
    }

    @JsonProperty("TickerSymbol")
    public void setTickerSymbol(String TickerSymbol) {
        this.TickerSymbol = TickerSymbol;
    }

    // Description
    private String Description;

    @JsonProperty("Description")
    public String getDescription() {
        return this.Description;
    }

    @JsonProperty("Description")
    public void setDescription(String Description) {
        this.Description = Description;
    }

    // Site
    private String Site;

    @JsonProperty("Site")
    public String getSite() {
        return this.Site;
    }

    @JsonProperty("Site")
    public void setSite(String Site) {
        this.Site = Site;
    }

    // Jigsaw
    private String Jigsaw;

    @JsonProperty("Jigsaw")
    public String getJigsaw() {
        return this.Jigsaw;
    }

    @JsonProperty("Jigsaw")
    public void setJigsaw(String Jigsaw) {
        this.Jigsaw = Jigsaw;
    }

    // JigsawCompanyId
    private String JigsawCompanyId;

    @JsonProperty("JigsawCompanyId")
    public String getJigsawCompanyId() {
        return this.JigsawCompanyId;
    }

    @JsonProperty("JigsawCompanyId")
    public void setJigsawCompanyId(String JigsawCompanyId) {
        this.JigsawCompanyId = JigsawCompanyId;
    }

    // DunsNumber
    private String DunsNumber;

    @JsonProperty("DunsNumber")
    public String getDunsNumber() {
        return this.DunsNumber;
    }

    @JsonProperty("DunsNumber")
    public void setDunsNumber(String DunsNumber) {
        this.DunsNumber = DunsNumber;
    }

    // Tradestyle
    private String Tradestyle;

    @JsonProperty("Tradestyle")
    public String getTradestyle() {
        return this.Tradestyle;
    }

    @JsonProperty("Tradestyle")
    public void setTradestyle(String Tradestyle) {
        this.Tradestyle = Tradestyle;
    }

    // NaicsCode
    private String NaicsCode;

    @JsonProperty("NaicsCode")
    public String getNaicsCode() {
        return this.NaicsCode;
    }

    @JsonProperty("NaicsCode")
    public void setNaicsCode(String NaicsCode) {
        this.NaicsCode = NaicsCode;
    }

    // NaicsDesc
    private String NaicsDesc;

    @JsonProperty("NaicsDesc")
    public String getNaicsDesc() {
        return this.NaicsDesc;
    }

    @JsonProperty("NaicsDesc")
    public void setNaicsDesc(String NaicsDesc) {
        this.NaicsDesc = NaicsDesc;
    }

    // YearStarted
    private String YearStarted;

    @JsonProperty("YearStarted")
    public String getYearStarted() {
        return this.YearStarted;
    }

    @JsonProperty("YearStarted")
    public void setYearStarted(String YearStarted) {
        this.YearStarted = YearStarted;
    }

    // SicDesc
    private String SicDesc;

    @JsonProperty("SicDesc")
    public String getSicDesc() {
        return this.SicDesc;
    }

    @JsonProperty("SicDesc")
    public void setSicDesc(String SicDesc) {
        this.SicDesc = SicDesc;
    }

    // DandbCompanyId
    private String DandbCompanyId;

    @JsonProperty("DandbCompanyId")
    public String getDandbCompanyId() {
        return this.DandbCompanyId;
    }

    @JsonProperty("DandbCompanyId")
    public void setDandbCompanyId(String DandbCompanyId) {
        this.DandbCompanyId = DandbCompanyId;
    }

    // NumberofLocations__c
    private Double NumberofLocations__c;

    @JsonProperty("NumberofLocations__c")
    public Double getNumberofLocations__c() {
        return this.NumberofLocations__c;
    }

    @JsonProperty("NumberofLocations__c")
    public void setNumberofLocations__c(Double NumberofLocations__c) {
        this.NumberofLocations__c = NumberofLocations__c;
    }

    // SLASerialNumber__c
    private String SLASerialNumber__c;

    @JsonProperty("SLASerialNumber__c")
    public String getSLASerialNumber__c() {
        return this.SLASerialNumber__c;
    }

    @JsonProperty("SLASerialNumber__c")
    public void setSLASerialNumber__c(String SLASerialNumber__c) {
        this.SLASerialNumber__c = SLASerialNumber__c;
    }

    // SLAExpirationDate__c
    private java.time.ZonedDateTime SLAExpirationDate__c;

    @JsonProperty("SLAExpirationDate__c")
    public java.time.ZonedDateTime getSLAExpirationDate__c() {
        return this.SLAExpirationDate__c;
    }

    @JsonProperty("SLAExpirationDate__c")
    public void setSLAExpirationDate__c(java.time.ZonedDateTime SLAExpirationDate__c) {
        this.SLAExpirationDate__c = SLAExpirationDate__c;
    }

    // Shipping_Location__Latitude__s
    private Double Shipping_Location__Latitude__s;

    @JsonProperty("Shipping_Location__Latitude__s")
    public Double getShipping_Location__Latitude__s() {
        return this.Shipping_Location__Latitude__s;
    }

    @JsonProperty("Shipping_Location__Latitude__s")
    public void setShipping_Location__Latitude__s(Double Shipping_Location__Latitude__s) {
        this.Shipping_Location__Latitude__s = Shipping_Location__Latitude__s;
    }

    // Shipping_Location__Longitude__s
    private Double Shipping_Location__Longitude__s;

    @JsonProperty("Shipping_Location__Longitude__s")
    public Double getShipping_Location__Longitude__s() {
        return this.Shipping_Location__Longitude__s;
    }

    @JsonProperty("Shipping_Location__Longitude__s")
    public void setShipping_Location__Longitude__s(Double Shipping_Location__Longitude__s) {
        this.Shipping_Location__Longitude__s = Shipping_Location__Longitude__s;
    }

    // Shipping_Location__c
    private org.apache.camel.component.salesforce.api.dto.GeoLocation Shipping_Location__c;

    @JsonProperty("Shipping_Location__c")
    public org.apache.camel.component.salesforce.api.dto.GeoLocation getShipping_Location__c() {
        return this.Shipping_Location__c;
    }

    @JsonProperty("Shipping_Location__c")
    public void setShipping_Location__c(org.apache.camel.component.salesforce.api.dto.GeoLocation Shipping_Location__c) {
        this.Shipping_Location__c = Shipping_Location__c;
    }


    @Override
    public final SObjectDescription description() {
        return DESCRIPTION;
    }

    private static SObjectDescription createSObjectDescription() {
        final SObjectDescription description = new SObjectDescription();


        description.setMergeable(true);
        description.setCreateable(true);
        description.setQueryable(true);
        description.setLabel("Account");
        description.setReplicateable(true);

        final List<RecordTypeInfo> recordTypeInfos1 = new ArrayList<>();
        description.setRecordTypeInfos(recordTypeInfos1);

        final RecordTypeInfo recordTypeInfo1 = new RecordTypeInfo();
        recordTypeInfos1.add(recordTypeInfo1);

        recordTypeInfo1.setDefaultRecordTypeMapping(true);
        recordTypeInfo1.setRecordTypeId("012000000000000AAA");
        recordTypeInfo1.setAvailable(true);

        final InfoUrls infoUrls1 = new InfoUrls();
        infoUrls1.setLayout("/services/data/v34.0/sobjects/Account/describe/layouts/012000000000000AAA");
        recordTypeInfo1.setUrls(infoUrls1);
        recordTypeInfo1.setName("Master");


        description.setName("Account");
        description.setLayoutable(true);
        description.setDeprecatedAndHidden(false);
        description.setSearchable(true);
        description.setFeedEnabled(true);
        description.setRetrieveable(true);
        description.setCustomSetting(false);
        description.setKeyPrefix("001");
        description.setUndeletable(true);
        description.setSearchLayoutable("true");
        description.setTriggerable(true);
        description.setCustom(false);

        final SObjectDescriptionUrls sObjectDescriptionUrls1 = new SObjectDescriptionUrls();
        sObjectDescriptionUrls1.setDescribe("/services/data/v34.0/sobjects/Account/describe");
        sObjectDescriptionUrls1.setLayouts("/services/data/v34.0/sobjects/Account/describe/layouts");
        sObjectDescriptionUrls1.setSobject("/services/data/v34.0/sobjects/Account");
        sObjectDescriptionUrls1.setQuickActions("/services/data/v34.0/sobjects/Account/quickActions");
        sObjectDescriptionUrls1.setUiEditTemplate("https://eu11.salesforce.com/{ID}/e");
        sObjectDescriptionUrls1.setRowTemplate("/services/data/v34.0/sobjects/Account/{ID}");
        sObjectDescriptionUrls1.setListviews("/services/data/v34.0/sobjects/Account/listviews");
        sObjectDescriptionUrls1.setCompactLayouts("/services/data/v34.0/sobjects/Account/describe/compactLayouts");
        sObjectDescriptionUrls1.setApprovalLayouts("/services/data/v34.0/sobjects/Account/describe/approvalLayouts");
        sObjectDescriptionUrls1.setUiNewRecord("https://eu11.salesforce.com/001/e");
        sObjectDescriptionUrls1.setUiDetailTemplate("https://eu11.salesforce.com/{ID}");
        description.setUrls(sObjectDescriptionUrls1);
        description.setCompactLayoutable(true);

        final List<SObjectField> fields1 = new ArrayList<>();
        description.setFields(fields1);

        final SObjectField sObjectField1 = new SObjectField();
        fields1.add(sObjectField1);

        sObjectField1.setWriteRequiresMasterRead(false);
        sObjectField1.setNillable(false);
        sObjectField1.setCreateable(false);
        sObjectField1.setEncrypted(false);
        sObjectField1.setDigits("0");
        sObjectField1.setDependentPicklist(false);
        sObjectField1.setLabel("Account ID");
        sObjectField1.setHighScaleNumber(false);
        sObjectField1.setDisplayLocationInDecimal(false);
        sObjectField1.setName("Id");
        sObjectField1.setHtmlFormatted(false);
        sObjectField1.setDeprecatedAndHidden(false);
        sObjectField1.setRestrictedPicklist(false);
        sObjectField1.setNameField(false);
        sObjectField1.setCaseSensitive(false);
        sObjectField1.setPermissionable(false);
        sObjectField1.setCascadeDelete(false);
        sObjectField1.setDefaultedOnCreate(true);
        sObjectField1.setExternalId(false);
        sObjectField1.setSoapType("tns:ID");
        sObjectField1.setGroupable(true);
        sObjectField1.setCustom(false);
        sObjectField1.setScale(0);
        sObjectField1.setCalculated(false);
        sObjectField1.setRestrictedDelete(false);
        sObjectField1.setNamePointing(false);
        sObjectField1.setIdLookup(true);
        sObjectField1.setType("id");
        sObjectField1.setSortable(true);
        sObjectField1.setLength(18);
        sObjectField1.setPrecision(0);
        sObjectField1.setByteLength(18);
        sObjectField1.setQueryByDistance(false);
        sObjectField1.setFilterable(true);
        sObjectField1.setUpdateable(false);
        sObjectField1.setUnique(false);
        sObjectField1.setAutoNumber(false);

        final SObjectField sObjectField2 = new SObjectField();
        fields1.add(sObjectField2);

        sObjectField2.setWriteRequiresMasterRead(false);
        sObjectField2.setNillable(false);
        sObjectField2.setCreateable(false);
        sObjectField2.setEncrypted(false);
        sObjectField2.setDigits("0");
        sObjectField2.setDependentPicklist(false);
        sObjectField2.setLabel("Deleted");
        sObjectField2.setHighScaleNumber(false);
        sObjectField2.setDisplayLocationInDecimal(false);
        sObjectField2.setName("IsDeleted");
        sObjectField2.setHtmlFormatted(false);
        sObjectField2.setDeprecatedAndHidden(false);
        sObjectField2.setRestrictedPicklist(false);
        sObjectField2.setNameField(false);
        sObjectField2.setCaseSensitive(false);
        sObjectField2.setPermissionable(false);
        sObjectField2.setCascadeDelete(false);
        sObjectField2.setDefaultedOnCreate(true);
        sObjectField2.setExternalId(false);
        sObjectField2.setSoapType("xsd:boolean");
        sObjectField2.setGroupable(true);
        sObjectField2.setCustom(false);
        sObjectField2.setScale(0);
        sObjectField2.setCalculated(false);
        sObjectField2.setRestrictedDelete(false);
        sObjectField2.setNamePointing(false);
        sObjectField2.setIdLookup(false);
        sObjectField2.setType("boolean");
        sObjectField2.setSortable(true);
        sObjectField2.setLength(0);
        sObjectField2.setPrecision(0);
        sObjectField2.setByteLength(0);
        sObjectField2.setQueryByDistance(false);
        sObjectField2.setFilterable(true);
        sObjectField2.setUpdateable(false);
        sObjectField2.setUnique(false);
        sObjectField2.setAutoNumber(false);

        final SObjectField sObjectField3 = new SObjectField();
        fields1.add(sObjectField3);

        sObjectField3.setWriteRequiresMasterRead(false);
        sObjectField3.setNillable(true);
        sObjectField3.setCreateable(false);
        sObjectField3.setEncrypted(false);
        sObjectField3.setDigits("0");
        sObjectField3.setDependentPicklist(false);
        sObjectField3.setLabel("Master Record ID");
        sObjectField3.setHighScaleNumber(false);
        sObjectField3.setDisplayLocationInDecimal(false);
        sObjectField3.setName("MasterRecordId");
        sObjectField3.setHtmlFormatted(false);
        sObjectField3.setDeprecatedAndHidden(false);
        sObjectField3.setRestrictedPicklist(false);
        sObjectField3.setNameField(false);
        sObjectField3.setCaseSensitive(false);
        sObjectField3.setPermissionable(false);
        sObjectField3.setCascadeDelete(false);
        sObjectField3.setDefaultedOnCreate(false);
        sObjectField3.setExternalId(false);
        sObjectField3.setSoapType("tns:ID");
        sObjectField3.setGroupable(true);
        sObjectField3.setCustom(false);
        sObjectField3.setScale(0);
        sObjectField3.setCalculated(false);
        sObjectField3.setRestrictedDelete(false);
        sObjectField3.setNamePointing(false);
        sObjectField3.setIdLookup(false);
        sObjectField3.setType("reference");

        final List<String> referenceTo1 = new ArrayList<>();
        sObjectField3.setReferenceTo(referenceTo1);

        referenceTo1.add("Account");

        sObjectField3.setRelationshipName("MasterRecord");
        sObjectField3.setSortable(true);
        sObjectField3.setLength(18);
        sObjectField3.setPrecision(0);
        sObjectField3.setByteLength(18);
        sObjectField3.setQueryByDistance(false);
        sObjectField3.setFilterable(true);
        sObjectField3.setUpdateable(false);
        sObjectField3.setUnique(false);
        sObjectField3.setAutoNumber(false);

        final SObjectField sObjectField4 = new SObjectField();
        fields1.add(sObjectField4);

        sObjectField4.setWriteRequiresMasterRead(false);
        sObjectField4.setNillable(false);
        sObjectField4.setCreateable(true);
        sObjectField4.setEncrypted(false);
        sObjectField4.setDigits("0");
        sObjectField4.setExtraTypeInfo("switchablepersonname");
        sObjectField4.setDependentPicklist(false);
        sObjectField4.setLabel("Account Name");
        sObjectField4.setHighScaleNumber(false);
        sObjectField4.setDisplayLocationInDecimal(false);
        sObjectField4.setName("Name");
        sObjectField4.setHtmlFormatted(false);
        sObjectField4.setDeprecatedAndHidden(false);
        sObjectField4.setRestrictedPicklist(false);
        sObjectField4.setNameField(true);
        sObjectField4.setCaseSensitive(false);
        sObjectField4.setPermissionable(false);
        sObjectField4.setCascadeDelete(false);
        sObjectField4.setDefaultedOnCreate(false);
        sObjectField4.setExternalId(false);
        sObjectField4.setSoapType("xsd:string");
        sObjectField4.setGroupable(true);
        sObjectField4.setCustom(false);
        sObjectField4.setScale(0);
        sObjectField4.setCalculated(false);
        sObjectField4.setRestrictedDelete(false);
        sObjectField4.setNamePointing(false);
        sObjectField4.setIdLookup(false);
        sObjectField4.setType("string");
        sObjectField4.setSortable(true);
        sObjectField4.setLength(255);
        sObjectField4.setPrecision(0);
        sObjectField4.setByteLength(765);
        sObjectField4.setQueryByDistance(false);
        sObjectField4.setFilterable(true);
        sObjectField4.setUpdateable(true);
        sObjectField4.setUnique(false);
        sObjectField4.setAutoNumber(false);

        final SObjectField sObjectField5 = new SObjectField();
        fields1.add(sObjectField5);

        sObjectField5.setWriteRequiresMasterRead(false);
        sObjectField5.setNillable(true);
        sObjectField5.setCreateable(true);
        sObjectField5.setEncrypted(false);
        sObjectField5.setDigits("0");
        sObjectField5.setDependentPicklist(false);
        sObjectField5.setLabel("Account Type");
        sObjectField5.setHighScaleNumber(false);
        sObjectField5.setDisplayLocationInDecimal(false);
        sObjectField5.setName("Type");
        sObjectField5.setHtmlFormatted(false);
        sObjectField5.setDeprecatedAndHidden(false);
        sObjectField5.setRestrictedPicklist(false);
        sObjectField5.setNameField(false);
        sObjectField5.setCaseSensitive(false);
        sObjectField5.setPermissionable(true);
        sObjectField5.setCascadeDelete(false);
        sObjectField5.setDefaultedOnCreate(false);
        sObjectField5.setExternalId(false);
        sObjectField5.setSoapType("xsd:string");
        sObjectField5.setGroupable(true);
        sObjectField5.setCustom(false);
        sObjectField5.setScale(0);
        sObjectField5.setCalculated(false);
        sObjectField5.setRestrictedDelete(false);
        sObjectField5.setNamePointing(false);
        sObjectField5.setIdLookup(false);
        sObjectField5.setType("picklist");
        sObjectField5.setSortable(true);
        sObjectField5.setLength(40);
        sObjectField5.setPrecision(0);
        sObjectField5.setByteLength(120);
        sObjectField5.setQueryByDistance(false);
        sObjectField5.setFilterable(true);
        sObjectField5.setUpdateable(true);
        sObjectField5.setUnique(false);
        sObjectField5.setAutoNumber(false);

        final SObjectField sObjectField6 = new SObjectField();
        fields1.add(sObjectField6);

        sObjectField6.setWriteRequiresMasterRead(false);
        sObjectField6.setNillable(true);
        sObjectField6.setCreateable(true);
        sObjectField6.setEncrypted(false);
        sObjectField6.setDigits("0");
        sObjectField6.setDependentPicklist(false);
        sObjectField6.setLabel("Parent Account ID");
        sObjectField6.setHighScaleNumber(false);
        sObjectField6.setDisplayLocationInDecimal(false);
        sObjectField6.setName("ParentId");
        sObjectField6.setHtmlFormatted(false);
        sObjectField6.setDeprecatedAndHidden(false);
        sObjectField6.setRestrictedPicklist(false);
        sObjectField6.setNameField(false);
        sObjectField6.setCaseSensitive(false);
        sObjectField6.setPermissionable(true);
        sObjectField6.setCascadeDelete(false);
        sObjectField6.setDefaultedOnCreate(false);
        sObjectField6.setExternalId(false);
        sObjectField6.setSoapType("tns:ID");
        sObjectField6.setGroupable(true);
        sObjectField6.setCustom(false);
        sObjectField6.setScale(0);
        sObjectField6.setCalculated(false);
        sObjectField6.setRestrictedDelete(false);
        sObjectField6.setNamePointing(false);
        sObjectField6.setIdLookup(false);
        sObjectField6.setType("reference");

        final List<String> referenceTo2 = new ArrayList<>();
        sObjectField6.setReferenceTo(referenceTo2);

        referenceTo2.add("Account");

        sObjectField6.setRelationshipName("Parent");
        sObjectField6.setSortable(true);
        sObjectField6.setLength(18);
        sObjectField6.setPrecision(0);
        sObjectField6.setByteLength(18);
        sObjectField6.setQueryByDistance(false);
        sObjectField6.setFilterable(true);
        sObjectField6.setUpdateable(true);
        sObjectField6.setUnique(false);
        sObjectField6.setAutoNumber(false);

        final SObjectField sObjectField7 = new SObjectField();
        fields1.add(sObjectField7);

        sObjectField7.setWriteRequiresMasterRead(false);
        sObjectField7.setNillable(true);
        sObjectField7.setCreateable(true);
        sObjectField7.setEncrypted(false);
        sObjectField7.setDigits("0");
        sObjectField7.setExtraTypeInfo("plaintextarea");
        sObjectField7.setDependentPicklist(false);
        sObjectField7.setLabel("Billing Street");
        sObjectField7.setHighScaleNumber(false);
        sObjectField7.setDisplayLocationInDecimal(false);
        sObjectField7.setName("BillingStreet");
        sObjectField7.setHtmlFormatted(false);
        sObjectField7.setDeprecatedAndHidden(false);
        sObjectField7.setRestrictedPicklist(false);
        sObjectField7.setNameField(false);
        sObjectField7.setCaseSensitive(false);
        sObjectField7.setPermissionable(true);
        sObjectField7.setCascadeDelete(false);
        sObjectField7.setDefaultedOnCreate(false);
        sObjectField7.setExternalId(false);
        sObjectField7.setSoapType("xsd:string");
        sObjectField7.setGroupable(true);
        sObjectField7.setCustom(false);
        sObjectField7.setScale(0);
        sObjectField7.setCalculated(false);
        sObjectField7.setRestrictedDelete(false);
        sObjectField7.setNamePointing(false);
        sObjectField7.setIdLookup(false);
        sObjectField7.setType("textarea");
        sObjectField7.setSortable(true);
        sObjectField7.setLength(255);
        sObjectField7.setPrecision(0);
        sObjectField7.setByteLength(765);
        sObjectField7.setQueryByDistance(false);
        sObjectField7.setFilterable(true);
        sObjectField7.setUpdateable(true);
        sObjectField7.setUnique(false);
        sObjectField7.setAutoNumber(false);

        final SObjectField sObjectField8 = new SObjectField();
        fields1.add(sObjectField8);

        sObjectField8.setWriteRequiresMasterRead(false);
        sObjectField8.setNillable(true);
        sObjectField8.setCreateable(true);
        sObjectField8.setEncrypted(false);
        sObjectField8.setDigits("0");
        sObjectField8.setDependentPicklist(false);
        sObjectField8.setLabel("Billing City");
        sObjectField8.setHighScaleNumber(false);
        sObjectField8.setDisplayLocationInDecimal(false);
        sObjectField8.setName("BillingCity");
        sObjectField8.setHtmlFormatted(false);
        sObjectField8.setDeprecatedAndHidden(false);
        sObjectField8.setRestrictedPicklist(false);
        sObjectField8.setNameField(false);
        sObjectField8.setCaseSensitive(false);
        sObjectField8.setPermissionable(true);
        sObjectField8.setCascadeDelete(false);
        sObjectField8.setDefaultedOnCreate(false);
        sObjectField8.setExternalId(false);
        sObjectField8.setSoapType("xsd:string");
        sObjectField8.setGroupable(true);
        sObjectField8.setCustom(false);
        sObjectField8.setScale(0);
        sObjectField8.setCalculated(false);
        sObjectField8.setRestrictedDelete(false);
        sObjectField8.setNamePointing(false);
        sObjectField8.setIdLookup(false);
        sObjectField8.setType("string");
        sObjectField8.setSortable(true);
        sObjectField8.setLength(40);
        sObjectField8.setPrecision(0);
        sObjectField8.setByteLength(120);
        sObjectField8.setQueryByDistance(false);
        sObjectField8.setFilterable(true);
        sObjectField8.setUpdateable(true);
        sObjectField8.setUnique(false);
        sObjectField8.setAutoNumber(false);

        final SObjectField sObjectField9 = new SObjectField();
        fields1.add(sObjectField9);

        sObjectField9.setWriteRequiresMasterRead(false);
        sObjectField9.setNillable(true);
        sObjectField9.setCreateable(true);
        sObjectField9.setEncrypted(false);
        sObjectField9.setDigits("0");
        sObjectField9.setDependentPicklist(false);
        sObjectField9.setLabel("Billing State/Province");
        sObjectField9.setHighScaleNumber(false);
        sObjectField9.setDisplayLocationInDecimal(false);
        sObjectField9.setName("BillingState");
        sObjectField9.setHtmlFormatted(false);
        sObjectField9.setDeprecatedAndHidden(false);
        sObjectField9.setRestrictedPicklist(false);
        sObjectField9.setNameField(false);
        sObjectField9.setCaseSensitive(false);
        sObjectField9.setPermissionable(true);
        sObjectField9.setCascadeDelete(false);
        sObjectField9.setDefaultedOnCreate(false);
        sObjectField9.setExternalId(false);
        sObjectField9.setSoapType("xsd:string");
        sObjectField9.setGroupable(true);
        sObjectField9.setCustom(false);
        sObjectField9.setScale(0);
        sObjectField9.setCalculated(false);
        sObjectField9.setRestrictedDelete(false);
        sObjectField9.setNamePointing(false);
        sObjectField9.setIdLookup(false);
        sObjectField9.setType("string");
        sObjectField9.setSortable(true);
        sObjectField9.setLength(80);
        sObjectField9.setPrecision(0);
        sObjectField9.setByteLength(240);
        sObjectField9.setQueryByDistance(false);
        sObjectField9.setFilterable(true);
        sObjectField9.setUpdateable(true);
        sObjectField9.setUnique(false);
        sObjectField9.setAutoNumber(false);

        final SObjectField sObjectField10 = new SObjectField();
        fields1.add(sObjectField10);

        sObjectField10.setWriteRequiresMasterRead(false);
        sObjectField10.setNillable(true);
        sObjectField10.setCreateable(true);
        sObjectField10.setEncrypted(false);
        sObjectField10.setDigits("0");
        sObjectField10.setDependentPicklist(false);
        sObjectField10.setLabel("Billing Zip/Postal Code");
        sObjectField10.setHighScaleNumber(false);
        sObjectField10.setDisplayLocationInDecimal(false);
        sObjectField10.setName("BillingPostalCode");
        sObjectField10.setHtmlFormatted(false);
        sObjectField10.setDeprecatedAndHidden(false);
        sObjectField10.setRestrictedPicklist(false);
        sObjectField10.setNameField(false);
        sObjectField10.setCaseSensitive(false);
        sObjectField10.setPermissionable(true);
        sObjectField10.setCascadeDelete(false);
        sObjectField10.setDefaultedOnCreate(false);
        sObjectField10.setExternalId(false);
        sObjectField10.setSoapType("xsd:string");
        sObjectField10.setGroupable(true);
        sObjectField10.setCustom(false);
        sObjectField10.setScale(0);
        sObjectField10.setCalculated(false);
        sObjectField10.setRestrictedDelete(false);
        sObjectField10.setNamePointing(false);
        sObjectField10.setIdLookup(false);
        sObjectField10.setType("string");
        sObjectField10.setSortable(true);
        sObjectField10.setLength(20);
        sObjectField10.setPrecision(0);
        sObjectField10.setByteLength(60);
        sObjectField10.setQueryByDistance(false);
        sObjectField10.setFilterable(true);
        sObjectField10.setUpdateable(true);
        sObjectField10.setUnique(false);
        sObjectField10.setAutoNumber(false);

        final SObjectField sObjectField11 = new SObjectField();
        fields1.add(sObjectField11);

        sObjectField11.setWriteRequiresMasterRead(false);
        sObjectField11.setNillable(true);
        sObjectField11.setCreateable(true);
        sObjectField11.setEncrypted(false);
        sObjectField11.setDigits("0");
        sObjectField11.setDependentPicklist(false);
        sObjectField11.setLabel("Billing Country");
        sObjectField11.setHighScaleNumber(false);
        sObjectField11.setDisplayLocationInDecimal(false);
        sObjectField11.setName("BillingCountry");
        sObjectField11.setHtmlFormatted(false);
        sObjectField11.setDeprecatedAndHidden(false);
        sObjectField11.setRestrictedPicklist(false);
        sObjectField11.setNameField(false);
        sObjectField11.setCaseSensitive(false);
        sObjectField11.setPermissionable(true);
        sObjectField11.setCascadeDelete(false);
        sObjectField11.setDefaultedOnCreate(false);
        sObjectField11.setExternalId(false);
        sObjectField11.setSoapType("xsd:string");
        sObjectField11.setGroupable(true);
        sObjectField11.setCustom(false);
        sObjectField11.setScale(0);
        sObjectField11.setCalculated(false);
        sObjectField11.setRestrictedDelete(false);
        sObjectField11.setNamePointing(false);
        sObjectField11.setIdLookup(false);
        sObjectField11.setType("string");
        sObjectField11.setSortable(true);
        sObjectField11.setLength(80);
        sObjectField11.setPrecision(0);
        sObjectField11.setByteLength(240);
        sObjectField11.setQueryByDistance(false);
        sObjectField11.setFilterable(true);
        sObjectField11.setUpdateable(true);
        sObjectField11.setUnique(false);
        sObjectField11.setAutoNumber(false);

        final SObjectField sObjectField12 = new SObjectField();
        fields1.add(sObjectField12);

        sObjectField12.setWriteRequiresMasterRead(false);
        sObjectField12.setNillable(true);
        sObjectField12.setCreateable(true);
        sObjectField12.setEncrypted(false);
        sObjectField12.setDigits("0");
        sObjectField12.setDependentPicklist(false);
        sObjectField12.setLabel("Billing Latitude");
        sObjectField12.setHighScaleNumber(false);
        sObjectField12.setDisplayLocationInDecimal(false);
        sObjectField12.setName("BillingLatitude");
        sObjectField12.setHtmlFormatted(false);
        sObjectField12.setDeprecatedAndHidden(false);
        sObjectField12.setRestrictedPicklist(false);
        sObjectField12.setNameField(false);
        sObjectField12.setCaseSensitive(false);
        sObjectField12.setPermissionable(true);
        sObjectField12.setCascadeDelete(false);
        sObjectField12.setDefaultedOnCreate(false);
        sObjectField12.setExternalId(false);
        sObjectField12.setSoapType("xsd:double");
        sObjectField12.setGroupable(false);
        sObjectField12.setCustom(false);
        sObjectField12.setScale(15);
        sObjectField12.setCalculated(false);
        sObjectField12.setRestrictedDelete(false);
        sObjectField12.setNamePointing(false);
        sObjectField12.setIdLookup(false);
        sObjectField12.setType("double");
        sObjectField12.setSortable(true);
        sObjectField12.setLength(0);
        sObjectField12.setPrecision(18);
        sObjectField12.setByteLength(0);
        sObjectField12.setQueryByDistance(false);
        sObjectField12.setFilterable(true);
        sObjectField12.setUpdateable(true);
        sObjectField12.setUnique(false);
        sObjectField12.setAutoNumber(false);

        final SObjectField sObjectField13 = new SObjectField();
        fields1.add(sObjectField13);

        sObjectField13.setWriteRequiresMasterRead(false);
        sObjectField13.setNillable(true);
        sObjectField13.setCreateable(true);
        sObjectField13.setEncrypted(false);
        sObjectField13.setDigits("0");
        sObjectField13.setDependentPicklist(false);
        sObjectField13.setLabel("Billing Longitude");
        sObjectField13.setHighScaleNumber(false);
        sObjectField13.setDisplayLocationInDecimal(false);
        sObjectField13.setName("BillingLongitude");
        sObjectField13.setHtmlFormatted(false);
        sObjectField13.setDeprecatedAndHidden(false);
        sObjectField13.setRestrictedPicklist(false);
        sObjectField13.setNameField(false);
        sObjectField13.setCaseSensitive(false);
        sObjectField13.setPermissionable(true);
        sObjectField13.setCascadeDelete(false);
        sObjectField13.setDefaultedOnCreate(false);
        sObjectField13.setExternalId(false);
        sObjectField13.setSoapType("xsd:double");
        sObjectField13.setGroupable(false);
        sObjectField13.setCustom(false);
        sObjectField13.setScale(15);
        sObjectField13.setCalculated(false);
        sObjectField13.setRestrictedDelete(false);
        sObjectField13.setNamePointing(false);
        sObjectField13.setIdLookup(false);
        sObjectField13.setType("double");
        sObjectField13.setSortable(true);
        sObjectField13.setLength(0);
        sObjectField13.setPrecision(18);
        sObjectField13.setByteLength(0);
        sObjectField13.setQueryByDistance(false);
        sObjectField13.setFilterable(true);
        sObjectField13.setUpdateable(true);
        sObjectField13.setUnique(false);
        sObjectField13.setAutoNumber(false);

        final SObjectField sObjectField14 = new SObjectField();
        fields1.add(sObjectField14);

        sObjectField14.setWriteRequiresMasterRead(false);
        sObjectField14.setNillable(true);
        sObjectField14.setCreateable(false);
        sObjectField14.setEncrypted(false);
        sObjectField14.setDigits("0");
        sObjectField14.setDependentPicklist(false);
        sObjectField14.setLabel("Billing Address");
        sObjectField14.setHighScaleNumber(false);
        sObjectField14.setDisplayLocationInDecimal(false);
        sObjectField14.setName("BillingAddress");
        sObjectField14.setHtmlFormatted(false);
        sObjectField14.setDeprecatedAndHidden(false);
        sObjectField14.setRestrictedPicklist(false);
        sObjectField14.setNameField(false);
        sObjectField14.setCaseSensitive(false);
        sObjectField14.setPermissionable(true);
        sObjectField14.setCascadeDelete(false);
        sObjectField14.setDefaultedOnCreate(false);
        sObjectField14.setExternalId(false);
        sObjectField14.setSoapType("urn:address");
        sObjectField14.setGroupable(false);
        sObjectField14.setCustom(false);
        sObjectField14.setScale(0);
        sObjectField14.setCalculated(false);
        sObjectField14.setRestrictedDelete(false);
        sObjectField14.setNamePointing(false);
        sObjectField14.setIdLookup(false);
        sObjectField14.setType("address");
        sObjectField14.setSortable(false);
        sObjectField14.setLength(0);
        sObjectField14.setPrecision(0);
        sObjectField14.setByteLength(0);
        sObjectField14.setQueryByDistance(true);
        sObjectField14.setFilterable(true);
        sObjectField14.setUpdateable(false);
        sObjectField14.setUnique(false);
        sObjectField14.setAutoNumber(false);

        final SObjectField sObjectField15 = new SObjectField();
        fields1.add(sObjectField15);

        sObjectField15.setWriteRequiresMasterRead(false);
        sObjectField15.setNillable(true);
        sObjectField15.setCreateable(true);
        sObjectField15.setEncrypted(false);
        sObjectField15.setDigits("0");
        sObjectField15.setExtraTypeInfo("plaintextarea");
        sObjectField15.setDependentPicklist(false);
        sObjectField15.setLabel("Shipping Street");
        sObjectField15.setHighScaleNumber(false);
        sObjectField15.setDisplayLocationInDecimal(false);
        sObjectField15.setName("ShippingStreet");
        sObjectField15.setHtmlFormatted(false);
        sObjectField15.setDeprecatedAndHidden(false);
        sObjectField15.setRestrictedPicklist(false);
        sObjectField15.setNameField(false);
        sObjectField15.setCaseSensitive(false);
        sObjectField15.setPermissionable(true);
        sObjectField15.setCascadeDelete(false);
        sObjectField15.setDefaultedOnCreate(false);
        sObjectField15.setExternalId(false);
        sObjectField15.setSoapType("xsd:string");
        sObjectField15.setGroupable(true);
        sObjectField15.setCustom(false);
        sObjectField15.setScale(0);
        sObjectField15.setCalculated(false);
        sObjectField15.setRestrictedDelete(false);
        sObjectField15.setNamePointing(false);
        sObjectField15.setIdLookup(false);
        sObjectField15.setType("textarea");
        sObjectField15.setSortable(true);
        sObjectField15.setLength(255);
        sObjectField15.setPrecision(0);
        sObjectField15.setByteLength(765);
        sObjectField15.setQueryByDistance(false);
        sObjectField15.setFilterable(true);
        sObjectField15.setUpdateable(true);
        sObjectField15.setUnique(false);
        sObjectField15.setAutoNumber(false);

        final SObjectField sObjectField16 = new SObjectField();
        fields1.add(sObjectField16);

        sObjectField16.setWriteRequiresMasterRead(false);
        sObjectField16.setNillable(true);
        sObjectField16.setCreateable(true);
        sObjectField16.setEncrypted(false);
        sObjectField16.setDigits("0");
        sObjectField16.setDependentPicklist(false);
        sObjectField16.setLabel("Shipping City");
        sObjectField16.setHighScaleNumber(false);
        sObjectField16.setDisplayLocationInDecimal(false);
        sObjectField16.setName("ShippingCity");
        sObjectField16.setHtmlFormatted(false);
        sObjectField16.setDeprecatedAndHidden(false);
        sObjectField16.setRestrictedPicklist(false);
        sObjectField16.setNameField(false);
        sObjectField16.setCaseSensitive(false);
        sObjectField16.setPermissionable(true);
        sObjectField16.setCascadeDelete(false);
        sObjectField16.setDefaultedOnCreate(false);
        sObjectField16.setExternalId(false);
        sObjectField16.setSoapType("xsd:string");
        sObjectField16.setGroupable(true);
        sObjectField16.setCustom(false);
        sObjectField16.setScale(0);
        sObjectField16.setCalculated(false);
        sObjectField16.setRestrictedDelete(false);
        sObjectField16.setNamePointing(false);
        sObjectField16.setIdLookup(false);
        sObjectField16.setType("string");
        sObjectField16.setSortable(true);
        sObjectField16.setLength(40);
        sObjectField16.setPrecision(0);
        sObjectField16.setByteLength(120);
        sObjectField16.setQueryByDistance(false);
        sObjectField16.setFilterable(true);
        sObjectField16.setUpdateable(true);
        sObjectField16.setUnique(false);
        sObjectField16.setAutoNumber(false);

        final SObjectField sObjectField17 = new SObjectField();
        fields1.add(sObjectField17);

        sObjectField17.setWriteRequiresMasterRead(false);
        sObjectField17.setNillable(true);
        sObjectField17.setCreateable(true);
        sObjectField17.setEncrypted(false);
        sObjectField17.setDigits("0");
        sObjectField17.setDependentPicklist(false);
        sObjectField17.setLabel("Shipping State/Province");
        sObjectField17.setHighScaleNumber(false);
        sObjectField17.setDisplayLocationInDecimal(false);
        sObjectField17.setName("ShippingState");
        sObjectField17.setHtmlFormatted(false);
        sObjectField17.setDeprecatedAndHidden(false);
        sObjectField17.setRestrictedPicklist(false);
        sObjectField17.setNameField(false);
        sObjectField17.setCaseSensitive(false);
        sObjectField17.setPermissionable(true);
        sObjectField17.setCascadeDelete(false);
        sObjectField17.setDefaultedOnCreate(false);
        sObjectField17.setExternalId(false);
        sObjectField17.setSoapType("xsd:string");
        sObjectField17.setGroupable(true);
        sObjectField17.setCustom(false);
        sObjectField17.setScale(0);
        sObjectField17.setCalculated(false);
        sObjectField17.setRestrictedDelete(false);
        sObjectField17.setNamePointing(false);
        sObjectField17.setIdLookup(false);
        sObjectField17.setType("string");
        sObjectField17.setSortable(true);
        sObjectField17.setLength(80);
        sObjectField17.setPrecision(0);
        sObjectField17.setByteLength(240);
        sObjectField17.setQueryByDistance(false);
        sObjectField17.setFilterable(true);
        sObjectField17.setUpdateable(true);
        sObjectField17.setUnique(false);
        sObjectField17.setAutoNumber(false);

        final SObjectField sObjectField18 = new SObjectField();
        fields1.add(sObjectField18);

        sObjectField18.setWriteRequiresMasterRead(false);
        sObjectField18.setNillable(true);
        sObjectField18.setCreateable(true);
        sObjectField18.setEncrypted(false);
        sObjectField18.setDigits("0");
        sObjectField18.setDependentPicklist(false);
        sObjectField18.setLabel("Shipping Zip/Postal Code");
        sObjectField18.setHighScaleNumber(false);
        sObjectField18.setDisplayLocationInDecimal(false);
        sObjectField18.setName("ShippingPostalCode");
        sObjectField18.setHtmlFormatted(false);
        sObjectField18.setDeprecatedAndHidden(false);
        sObjectField18.setRestrictedPicklist(false);
        sObjectField18.setNameField(false);
        sObjectField18.setCaseSensitive(false);
        sObjectField18.setPermissionable(true);
        sObjectField18.setCascadeDelete(false);
        sObjectField18.setDefaultedOnCreate(false);
        sObjectField18.setExternalId(false);
        sObjectField18.setSoapType("xsd:string");
        sObjectField18.setGroupable(true);
        sObjectField18.setCustom(false);
        sObjectField18.setScale(0);
        sObjectField18.setCalculated(false);
        sObjectField18.setRestrictedDelete(false);
        sObjectField18.setNamePointing(false);
        sObjectField18.setIdLookup(false);
        sObjectField18.setType("string");
        sObjectField18.setSortable(true);
        sObjectField18.setLength(20);
        sObjectField18.setPrecision(0);
        sObjectField18.setByteLength(60);
        sObjectField18.setQueryByDistance(false);
        sObjectField18.setFilterable(true);
        sObjectField18.setUpdateable(true);
        sObjectField18.setUnique(false);
        sObjectField18.setAutoNumber(false);

        final SObjectField sObjectField19 = new SObjectField();
        fields1.add(sObjectField19);

        sObjectField19.setWriteRequiresMasterRead(false);
        sObjectField19.setNillable(true);
        sObjectField19.setCreateable(true);
        sObjectField19.setEncrypted(false);
        sObjectField19.setDigits("0");
        sObjectField19.setDependentPicklist(false);
        sObjectField19.setLabel("Shipping Country");
        sObjectField19.setHighScaleNumber(false);
        sObjectField19.setDisplayLocationInDecimal(false);
        sObjectField19.setName("ShippingCountry");
        sObjectField19.setHtmlFormatted(false);
        sObjectField19.setDeprecatedAndHidden(false);
        sObjectField19.setRestrictedPicklist(false);
        sObjectField19.setNameField(false);
        sObjectField19.setCaseSensitive(false);
        sObjectField19.setPermissionable(true);
        sObjectField19.setCascadeDelete(false);
        sObjectField19.setDefaultedOnCreate(false);
        sObjectField19.setExternalId(false);
        sObjectField19.setSoapType("xsd:string");
        sObjectField19.setGroupable(true);
        sObjectField19.setCustom(false);
        sObjectField19.setScale(0);
        sObjectField19.setCalculated(false);
        sObjectField19.setRestrictedDelete(false);
        sObjectField19.setNamePointing(false);
        sObjectField19.setIdLookup(false);
        sObjectField19.setType("string");
        sObjectField19.setSortable(true);
        sObjectField19.setLength(80);
        sObjectField19.setPrecision(0);
        sObjectField19.setByteLength(240);
        sObjectField19.setQueryByDistance(false);
        sObjectField19.setFilterable(true);
        sObjectField19.setUpdateable(true);
        sObjectField19.setUnique(false);
        sObjectField19.setAutoNumber(false);

        final SObjectField sObjectField20 = new SObjectField();
        fields1.add(sObjectField20);

        sObjectField20.setWriteRequiresMasterRead(false);
        sObjectField20.setNillable(true);
        sObjectField20.setCreateable(true);
        sObjectField20.setEncrypted(false);
        sObjectField20.setDigits("0");
        sObjectField20.setDependentPicklist(false);
        sObjectField20.setLabel("Shipping Latitude");
        sObjectField20.setHighScaleNumber(false);
        sObjectField20.setDisplayLocationInDecimal(false);
        sObjectField20.setName("ShippingLatitude");
        sObjectField20.setHtmlFormatted(false);
        sObjectField20.setDeprecatedAndHidden(false);
        sObjectField20.setRestrictedPicklist(false);
        sObjectField20.setNameField(false);
        sObjectField20.setCaseSensitive(false);
        sObjectField20.setPermissionable(true);
        sObjectField20.setCascadeDelete(false);
        sObjectField20.setDefaultedOnCreate(false);
        sObjectField20.setExternalId(false);
        sObjectField20.setSoapType("xsd:double");
        sObjectField20.setGroupable(false);
        sObjectField20.setCustom(false);
        sObjectField20.setScale(15);
        sObjectField20.setCalculated(false);
        sObjectField20.setRestrictedDelete(false);
        sObjectField20.setNamePointing(false);
        sObjectField20.setIdLookup(false);
        sObjectField20.setType("double");
        sObjectField20.setSortable(true);
        sObjectField20.setLength(0);
        sObjectField20.setPrecision(18);
        sObjectField20.setByteLength(0);
        sObjectField20.setQueryByDistance(false);
        sObjectField20.setFilterable(true);
        sObjectField20.setUpdateable(true);
        sObjectField20.setUnique(false);
        sObjectField20.setAutoNumber(false);

        final SObjectField sObjectField21 = new SObjectField();
        fields1.add(sObjectField21);

        sObjectField21.setWriteRequiresMasterRead(false);
        sObjectField21.setNillable(true);
        sObjectField21.setCreateable(true);
        sObjectField21.setEncrypted(false);
        sObjectField21.setDigits("0");
        sObjectField21.setDependentPicklist(false);
        sObjectField21.setLabel("Shipping Longitude");
        sObjectField21.setHighScaleNumber(false);
        sObjectField21.setDisplayLocationInDecimal(false);
        sObjectField21.setName("ShippingLongitude");
        sObjectField21.setHtmlFormatted(false);
        sObjectField21.setDeprecatedAndHidden(false);
        sObjectField21.setRestrictedPicklist(false);
        sObjectField21.setNameField(false);
        sObjectField21.setCaseSensitive(false);
        sObjectField21.setPermissionable(true);
        sObjectField21.setCascadeDelete(false);
        sObjectField21.setDefaultedOnCreate(false);
        sObjectField21.setExternalId(false);
        sObjectField21.setSoapType("xsd:double");
        sObjectField21.setGroupable(false);
        sObjectField21.setCustom(false);
        sObjectField21.setScale(15);
        sObjectField21.setCalculated(false);
        sObjectField21.setRestrictedDelete(false);
        sObjectField21.setNamePointing(false);
        sObjectField21.setIdLookup(false);
        sObjectField21.setType("double");
        sObjectField21.setSortable(true);
        sObjectField21.setLength(0);
        sObjectField21.setPrecision(18);
        sObjectField21.setByteLength(0);
        sObjectField21.setQueryByDistance(false);
        sObjectField21.setFilterable(true);
        sObjectField21.setUpdateable(true);
        sObjectField21.setUnique(false);
        sObjectField21.setAutoNumber(false);

        final SObjectField sObjectField22 = new SObjectField();
        fields1.add(sObjectField22);

        sObjectField22.setWriteRequiresMasterRead(false);
        sObjectField22.setNillable(true);
        sObjectField22.setCreateable(false);
        sObjectField22.setEncrypted(false);
        sObjectField22.setDigits("0");
        sObjectField22.setDependentPicklist(false);
        sObjectField22.setLabel("Shipping Address");
        sObjectField22.setHighScaleNumber(false);
        sObjectField22.setDisplayLocationInDecimal(false);
        sObjectField22.setName("ShippingAddress");
        sObjectField22.setHtmlFormatted(false);
        sObjectField22.setDeprecatedAndHidden(false);
        sObjectField22.setRestrictedPicklist(false);
        sObjectField22.setNameField(false);
        sObjectField22.setCaseSensitive(false);
        sObjectField22.setPermissionable(true);
        sObjectField22.setCascadeDelete(false);
        sObjectField22.setDefaultedOnCreate(false);
        sObjectField22.setExternalId(false);
        sObjectField22.setSoapType("urn:address");
        sObjectField22.setGroupable(false);
        sObjectField22.setCustom(false);
        sObjectField22.setScale(0);
        sObjectField22.setCalculated(false);
        sObjectField22.setRestrictedDelete(false);
        sObjectField22.setNamePointing(false);
        sObjectField22.setIdLookup(false);
        sObjectField22.setType("address");
        sObjectField22.setSortable(false);
        sObjectField22.setLength(0);
        sObjectField22.setPrecision(0);
        sObjectField22.setByteLength(0);
        sObjectField22.setQueryByDistance(true);
        sObjectField22.setFilterable(true);
        sObjectField22.setUpdateable(false);
        sObjectField22.setUnique(false);
        sObjectField22.setAutoNumber(false);

        final SObjectField sObjectField23 = new SObjectField();
        fields1.add(sObjectField23);

        sObjectField23.setWriteRequiresMasterRead(false);
        sObjectField23.setNillable(true);
        sObjectField23.setCreateable(true);
        sObjectField23.setEncrypted(false);
        sObjectField23.setDigits("0");
        sObjectField23.setDependentPicklist(false);
        sObjectField23.setLabel("Account Phone");
        sObjectField23.setHighScaleNumber(false);
        sObjectField23.setDisplayLocationInDecimal(false);
        sObjectField23.setName("Phone");
        sObjectField23.setHtmlFormatted(false);
        sObjectField23.setDeprecatedAndHidden(false);
        sObjectField23.setRestrictedPicklist(false);
        sObjectField23.setNameField(false);
        sObjectField23.setCaseSensitive(false);
        sObjectField23.setPermissionable(true);
        sObjectField23.setCascadeDelete(false);
        sObjectField23.setDefaultedOnCreate(false);
        sObjectField23.setExternalId(false);
        sObjectField23.setSoapType("xsd:string");
        sObjectField23.setGroupable(true);
        sObjectField23.setCustom(false);
        sObjectField23.setScale(0);
        sObjectField23.setCalculated(false);
        sObjectField23.setRestrictedDelete(false);
        sObjectField23.setNamePointing(false);
        sObjectField23.setIdLookup(false);
        sObjectField23.setType("phone");
        sObjectField23.setSortable(true);
        sObjectField23.setLength(40);
        sObjectField23.setPrecision(0);
        sObjectField23.setByteLength(120);
        sObjectField23.setQueryByDistance(false);
        sObjectField23.setFilterable(true);
        sObjectField23.setUpdateable(true);
        sObjectField23.setUnique(false);
        sObjectField23.setAutoNumber(false);

        final SObjectField sObjectField24 = new SObjectField();
        fields1.add(sObjectField24);

        sObjectField24.setWriteRequiresMasterRead(false);
        sObjectField24.setNillable(true);
        sObjectField24.setCreateable(true);
        sObjectField24.setEncrypted(false);
        sObjectField24.setDigits("0");
        sObjectField24.setDependentPicklist(false);
        sObjectField24.setLabel("Account Fax");
        sObjectField24.setHighScaleNumber(false);
        sObjectField24.setDisplayLocationInDecimal(false);
        sObjectField24.setName("Fax");
        sObjectField24.setHtmlFormatted(false);
        sObjectField24.setDeprecatedAndHidden(false);
        sObjectField24.setRestrictedPicklist(false);
        sObjectField24.setNameField(false);
        sObjectField24.setCaseSensitive(false);
        sObjectField24.setPermissionable(true);
        sObjectField24.setCascadeDelete(false);
        sObjectField24.setDefaultedOnCreate(false);
        sObjectField24.setExternalId(false);
        sObjectField24.setSoapType("xsd:string");
        sObjectField24.setGroupable(true);
        sObjectField24.setCustom(false);
        sObjectField24.setScale(0);
        sObjectField24.setCalculated(false);
        sObjectField24.setRestrictedDelete(false);
        sObjectField24.setNamePointing(false);
        sObjectField24.setIdLookup(false);
        sObjectField24.setType("phone");
        sObjectField24.setSortable(true);
        sObjectField24.setLength(40);
        sObjectField24.setPrecision(0);
        sObjectField24.setByteLength(120);
        sObjectField24.setQueryByDistance(false);
        sObjectField24.setFilterable(true);
        sObjectField24.setUpdateable(true);
        sObjectField24.setUnique(false);
        sObjectField24.setAutoNumber(false);

        final SObjectField sObjectField25 = new SObjectField();
        fields1.add(sObjectField25);

        sObjectField25.setWriteRequiresMasterRead(false);
        sObjectField25.setNillable(true);
        sObjectField25.setCreateable(true);
        sObjectField25.setEncrypted(false);
        sObjectField25.setDigits("0");
        sObjectField25.setDependentPicklist(false);
        sObjectField25.setLabel("Account Number");
        sObjectField25.setHighScaleNumber(false);
        sObjectField25.setDisplayLocationInDecimal(false);
        sObjectField25.setName("AccountNumber");
        sObjectField25.setHtmlFormatted(false);
        sObjectField25.setDeprecatedAndHidden(false);
        sObjectField25.setRestrictedPicklist(false);
        sObjectField25.setNameField(false);
        sObjectField25.setCaseSensitive(false);
        sObjectField25.setPermissionable(true);
        sObjectField25.setCascadeDelete(false);
        sObjectField25.setDefaultedOnCreate(false);
        sObjectField25.setExternalId(false);
        sObjectField25.setSoapType("xsd:string");
        sObjectField25.setGroupable(true);
        sObjectField25.setCustom(false);
        sObjectField25.setScale(0);
        sObjectField25.setCalculated(false);
        sObjectField25.setRestrictedDelete(false);
        sObjectField25.setNamePointing(false);
        sObjectField25.setIdLookup(false);
        sObjectField25.setType("string");
        sObjectField25.setSortable(true);
        sObjectField25.setLength(40);
        sObjectField25.setPrecision(0);
        sObjectField25.setByteLength(120);
        sObjectField25.setQueryByDistance(false);
        sObjectField25.setFilterable(true);
        sObjectField25.setUpdateable(true);
        sObjectField25.setUnique(false);
        sObjectField25.setAutoNumber(false);

        final SObjectField sObjectField26 = new SObjectField();
        fields1.add(sObjectField26);

        sObjectField26.setWriteRequiresMasterRead(false);
        sObjectField26.setNillable(true);
        sObjectField26.setCreateable(true);
        sObjectField26.setEncrypted(false);
        sObjectField26.setDigits("0");
        sObjectField26.setDependentPicklist(false);
        sObjectField26.setLabel("Website");
        sObjectField26.setHighScaleNumber(false);
        sObjectField26.setDisplayLocationInDecimal(false);
        sObjectField26.setName("Website");
        sObjectField26.setHtmlFormatted(false);
        sObjectField26.setDeprecatedAndHidden(false);
        sObjectField26.setRestrictedPicklist(false);
        sObjectField26.setNameField(false);
        sObjectField26.setCaseSensitive(false);
        sObjectField26.setPermissionable(true);
        sObjectField26.setCascadeDelete(false);
        sObjectField26.setDefaultedOnCreate(false);
        sObjectField26.setExternalId(false);
        sObjectField26.setSoapType("xsd:string");
        sObjectField26.setGroupable(true);
        sObjectField26.setCustom(false);
        sObjectField26.setScale(0);
        sObjectField26.setCalculated(false);
        sObjectField26.setRestrictedDelete(false);
        sObjectField26.setNamePointing(false);
        sObjectField26.setIdLookup(false);
        sObjectField26.setType("url");
        sObjectField26.setSortable(true);
        sObjectField26.setLength(255);
        sObjectField26.setPrecision(0);
        sObjectField26.setByteLength(765);
        sObjectField26.setQueryByDistance(false);
        sObjectField26.setFilterable(true);
        sObjectField26.setUpdateable(true);
        sObjectField26.setUnique(false);
        sObjectField26.setAutoNumber(false);

        final SObjectField sObjectField27 = new SObjectField();
        fields1.add(sObjectField27);

        sObjectField27.setWriteRequiresMasterRead(false);
        sObjectField27.setNillable(true);
        sObjectField27.setCreateable(false);
        sObjectField27.setEncrypted(false);
        sObjectField27.setDigits("0");
        sObjectField27.setExtraTypeInfo("imageurl");
        sObjectField27.setDependentPicklist(false);
        sObjectField27.setLabel("Photo URL");
        sObjectField27.setHighScaleNumber(false);
        sObjectField27.setDisplayLocationInDecimal(false);
        sObjectField27.setName("PhotoUrl");
        sObjectField27.setHtmlFormatted(false);
        sObjectField27.setDeprecatedAndHidden(false);
        sObjectField27.setRestrictedPicklist(false);
        sObjectField27.setNameField(false);
        sObjectField27.setCaseSensitive(false);
        sObjectField27.setPermissionable(false);
        sObjectField27.setCascadeDelete(false);
        sObjectField27.setDefaultedOnCreate(false);
        sObjectField27.setExternalId(false);
        sObjectField27.setSoapType("xsd:string");
        sObjectField27.setGroupable(true);
        sObjectField27.setCustom(false);
        sObjectField27.setScale(0);
        sObjectField27.setCalculated(false);
        sObjectField27.setRestrictedDelete(false);
        sObjectField27.setNamePointing(false);
        sObjectField27.setIdLookup(false);
        sObjectField27.setType("url");
        sObjectField27.setSortable(true);
        sObjectField27.setLength(255);
        sObjectField27.setPrecision(0);
        sObjectField27.setByteLength(765);
        sObjectField27.setQueryByDistance(false);
        sObjectField27.setFilterable(true);
        sObjectField27.setUpdateable(false);
        sObjectField27.setUnique(false);
        sObjectField27.setAutoNumber(false);

        final SObjectField sObjectField28 = new SObjectField();
        fields1.add(sObjectField28);

        sObjectField28.setWriteRequiresMasterRead(false);
        sObjectField28.setNillable(true);
        sObjectField28.setCreateable(true);
        sObjectField28.setEncrypted(false);
        sObjectField28.setDigits("0");
        sObjectField28.setDependentPicklist(false);
        sObjectField28.setLabel("SIC Code");
        sObjectField28.setHighScaleNumber(false);
        sObjectField28.setDisplayLocationInDecimal(false);
        sObjectField28.setName("Sic");
        sObjectField28.setHtmlFormatted(false);
        sObjectField28.setDeprecatedAndHidden(false);
        sObjectField28.setRestrictedPicklist(false);
        sObjectField28.setNameField(false);
        sObjectField28.setCaseSensitive(false);
        sObjectField28.setPermissionable(true);
        sObjectField28.setCascadeDelete(false);
        sObjectField28.setDefaultedOnCreate(false);
        sObjectField28.setExternalId(false);
        sObjectField28.setSoapType("xsd:string");
        sObjectField28.setGroupable(true);
        sObjectField28.setCustom(false);
        sObjectField28.setScale(0);
        sObjectField28.setCalculated(false);
        sObjectField28.setRestrictedDelete(false);
        sObjectField28.setNamePointing(false);
        sObjectField28.setIdLookup(false);
        sObjectField28.setType("string");
        sObjectField28.setSortable(true);
        sObjectField28.setLength(20);
        sObjectField28.setPrecision(0);
        sObjectField28.setByteLength(60);
        sObjectField28.setQueryByDistance(false);
        sObjectField28.setFilterable(true);
        sObjectField28.setUpdateable(true);
        sObjectField28.setUnique(false);
        sObjectField28.setAutoNumber(false);

        final SObjectField sObjectField29 = new SObjectField();
        fields1.add(sObjectField29);

        sObjectField29.setWriteRequiresMasterRead(false);
        sObjectField29.setNillable(true);
        sObjectField29.setCreateable(true);
        sObjectField29.setEncrypted(false);
        sObjectField29.setDigits("0");
        sObjectField29.setDependentPicklist(false);
        sObjectField29.setLabel("Industry");
        sObjectField29.setHighScaleNumber(false);
        sObjectField29.setDisplayLocationInDecimal(false);
        sObjectField29.setName("Industry");
        sObjectField29.setHtmlFormatted(false);
        sObjectField29.setDeprecatedAndHidden(false);
        sObjectField29.setRestrictedPicklist(false);
        sObjectField29.setNameField(false);
        sObjectField29.setCaseSensitive(false);
        sObjectField29.setPermissionable(true);
        sObjectField29.setCascadeDelete(false);
        sObjectField29.setDefaultedOnCreate(false);
        sObjectField29.setExternalId(false);
        sObjectField29.setSoapType("xsd:string");
        sObjectField29.setGroupable(true);
        sObjectField29.setCustom(false);
        sObjectField29.setScale(0);
        sObjectField29.setCalculated(false);
        sObjectField29.setRestrictedDelete(false);
        sObjectField29.setNamePointing(false);
        sObjectField29.setIdLookup(false);
        sObjectField29.setType("picklist");
        sObjectField29.setSortable(true);
        sObjectField29.setLength(40);
        sObjectField29.setPrecision(0);
        sObjectField29.setByteLength(120);
        sObjectField29.setQueryByDistance(false);
        sObjectField29.setFilterable(true);
        sObjectField29.setUpdateable(true);
        sObjectField29.setUnique(false);
        sObjectField29.setAutoNumber(false);

        final SObjectField sObjectField30 = new SObjectField();
        fields1.add(sObjectField30);

        sObjectField30.setWriteRequiresMasterRead(false);
        sObjectField30.setNillable(true);
        sObjectField30.setCreateable(true);
        sObjectField30.setEncrypted(false);
        sObjectField30.setDigits("0");
        sObjectField30.setDependentPicklist(false);
        sObjectField30.setLabel("Annual Revenue");
        sObjectField30.setHighScaleNumber(false);
        sObjectField30.setDisplayLocationInDecimal(false);
        sObjectField30.setName("AnnualRevenue");
        sObjectField30.setHtmlFormatted(false);
        sObjectField30.setDeprecatedAndHidden(false);
        sObjectField30.setRestrictedPicklist(false);
        sObjectField30.setNameField(false);
        sObjectField30.setCaseSensitive(false);
        sObjectField30.setPermissionable(true);
        sObjectField30.setCascadeDelete(false);
        sObjectField30.setDefaultedOnCreate(false);
        sObjectField30.setExternalId(false);
        sObjectField30.setSoapType("xsd:double");
        sObjectField30.setGroupable(false);
        sObjectField30.setCustom(false);
        sObjectField30.setScale(0);
        sObjectField30.setCalculated(false);
        sObjectField30.setRestrictedDelete(false);
        sObjectField30.setNamePointing(false);
        sObjectField30.setIdLookup(false);
        sObjectField30.setType("currency");
        sObjectField30.setSortable(true);
        sObjectField30.setLength(0);
        sObjectField30.setPrecision(18);
        sObjectField30.setByteLength(0);
        sObjectField30.setQueryByDistance(false);
        sObjectField30.setFilterable(true);
        sObjectField30.setUpdateable(true);
        sObjectField30.setUnique(false);
        sObjectField30.setAutoNumber(false);

        final SObjectField sObjectField31 = new SObjectField();
        fields1.add(sObjectField31);

        sObjectField31.setWriteRequiresMasterRead(false);
        sObjectField31.setNillable(true);
        sObjectField31.setCreateable(true);
        sObjectField31.setEncrypted(false);
        sObjectField31.setDigits("8");
        sObjectField31.setDependentPicklist(false);
        sObjectField31.setLabel("Employees");
        sObjectField31.setHighScaleNumber(false);
        sObjectField31.setDisplayLocationInDecimal(false);
        sObjectField31.setName("NumberOfEmployees");
        sObjectField31.setHtmlFormatted(false);
        sObjectField31.setDeprecatedAndHidden(false);
        sObjectField31.setRestrictedPicklist(false);
        sObjectField31.setNameField(false);
        sObjectField31.setCaseSensitive(false);
        sObjectField31.setPermissionable(true);
        sObjectField31.setCascadeDelete(false);
        sObjectField31.setDefaultedOnCreate(false);
        sObjectField31.setExternalId(false);
        sObjectField31.setSoapType("xsd:int");
        sObjectField31.setGroupable(true);
        sObjectField31.setCustom(false);
        sObjectField31.setScale(0);
        sObjectField31.setCalculated(false);
        sObjectField31.setRestrictedDelete(false);
        sObjectField31.setNamePointing(false);
        sObjectField31.setIdLookup(false);
        sObjectField31.setType("int");
        sObjectField31.setSortable(true);
        sObjectField31.setLength(0);
        sObjectField31.setPrecision(0);
        sObjectField31.setByteLength(0);
        sObjectField31.setQueryByDistance(false);
        sObjectField31.setFilterable(true);
        sObjectField31.setUpdateable(true);
        sObjectField31.setUnique(false);
        sObjectField31.setAutoNumber(false);

        final SObjectField sObjectField32 = new SObjectField();
        fields1.add(sObjectField32);

        sObjectField32.setWriteRequiresMasterRead(false);
        sObjectField32.setNillable(true);
        sObjectField32.setCreateable(true);
        sObjectField32.setEncrypted(false);
        sObjectField32.setDigits("0");
        sObjectField32.setDependentPicklist(false);
        sObjectField32.setLabel("Ownership");
        sObjectField32.setHighScaleNumber(false);
        sObjectField32.setDisplayLocationInDecimal(false);
        sObjectField32.setName("Ownership");
        sObjectField32.setHtmlFormatted(false);
        sObjectField32.setDeprecatedAndHidden(false);
        sObjectField32.setRestrictedPicklist(false);
        sObjectField32.setNameField(false);
        sObjectField32.setCaseSensitive(false);
        sObjectField32.setPermissionable(true);
        sObjectField32.setCascadeDelete(false);
        sObjectField32.setDefaultedOnCreate(false);
        sObjectField32.setExternalId(false);
        sObjectField32.setSoapType("xsd:string");
        sObjectField32.setGroupable(true);
        sObjectField32.setCustom(false);
        sObjectField32.setScale(0);
        sObjectField32.setCalculated(false);
        sObjectField32.setRestrictedDelete(false);
        sObjectField32.setNamePointing(false);
        sObjectField32.setIdLookup(false);
        sObjectField32.setType("picklist");
        sObjectField32.setSortable(true);
        sObjectField32.setLength(40);
        sObjectField32.setPrecision(0);
        sObjectField32.setByteLength(120);
        sObjectField32.setQueryByDistance(false);
        sObjectField32.setFilterable(true);
        sObjectField32.setUpdateable(true);
        sObjectField32.setUnique(false);
        sObjectField32.setAutoNumber(false);

        final SObjectField sObjectField33 = new SObjectField();
        fields1.add(sObjectField33);

        sObjectField33.setWriteRequiresMasterRead(false);
        sObjectField33.setNillable(true);
        sObjectField33.setCreateable(true);
        sObjectField33.setEncrypted(false);
        sObjectField33.setDigits("0");
        sObjectField33.setDependentPicklist(false);
        sObjectField33.setLabel("Ticker Symbol");
        sObjectField33.setHighScaleNumber(false);
        sObjectField33.setDisplayLocationInDecimal(false);
        sObjectField33.setName("TickerSymbol");
        sObjectField33.setHtmlFormatted(false);
        sObjectField33.setDeprecatedAndHidden(false);
        sObjectField33.setRestrictedPicklist(false);
        sObjectField33.setNameField(false);
        sObjectField33.setCaseSensitive(false);
        sObjectField33.setPermissionable(true);
        sObjectField33.setCascadeDelete(false);
        sObjectField33.setDefaultedOnCreate(false);
        sObjectField33.setExternalId(false);
        sObjectField33.setSoapType("xsd:string");
        sObjectField33.setGroupable(true);
        sObjectField33.setCustom(false);
        sObjectField33.setScale(0);
        sObjectField33.setCalculated(false);
        sObjectField33.setRestrictedDelete(false);
        sObjectField33.setNamePointing(false);
        sObjectField33.setIdLookup(false);
        sObjectField33.setType("string");
        sObjectField33.setSortable(true);
        sObjectField33.setLength(20);
        sObjectField33.setPrecision(0);
        sObjectField33.setByteLength(60);
        sObjectField33.setQueryByDistance(false);
        sObjectField33.setFilterable(true);
        sObjectField33.setUpdateable(true);
        sObjectField33.setUnique(false);
        sObjectField33.setAutoNumber(false);

        final SObjectField sObjectField34 = new SObjectField();
        fields1.add(sObjectField34);

        sObjectField34.setWriteRequiresMasterRead(false);
        sObjectField34.setNillable(true);
        sObjectField34.setCreateable(true);
        sObjectField34.setEncrypted(false);
        sObjectField34.setDigits("0");
        sObjectField34.setExtraTypeInfo("plaintextarea");
        sObjectField34.setDependentPicklist(false);
        sObjectField34.setLabel("Account Description");
        sObjectField34.setHighScaleNumber(false);
        sObjectField34.setDisplayLocationInDecimal(false);
        sObjectField34.setName("Description");
        sObjectField34.setHtmlFormatted(false);
        sObjectField34.setDeprecatedAndHidden(false);
        sObjectField34.setRestrictedPicklist(false);
        sObjectField34.setNameField(false);
        sObjectField34.setCaseSensitive(false);
        sObjectField34.setPermissionable(true);
        sObjectField34.setCascadeDelete(false);
        sObjectField34.setDefaultedOnCreate(false);
        sObjectField34.setExternalId(false);
        sObjectField34.setSoapType("xsd:string");
        sObjectField34.setGroupable(false);
        sObjectField34.setCustom(false);
        sObjectField34.setScale(0);
        sObjectField34.setCalculated(false);
        sObjectField34.setRestrictedDelete(false);
        sObjectField34.setNamePointing(false);
        sObjectField34.setIdLookup(false);
        sObjectField34.setType("textarea");
        sObjectField34.setSortable(false);
        sObjectField34.setLength(32000);
        sObjectField34.setPrecision(0);
        sObjectField34.setByteLength(96000);
        sObjectField34.setQueryByDistance(false);
        sObjectField34.setFilterable(false);
        sObjectField34.setUpdateable(true);
        sObjectField34.setUnique(false);
        sObjectField34.setAutoNumber(false);

        final SObjectField sObjectField35 = new SObjectField();
        fields1.add(sObjectField35);

        sObjectField35.setWriteRequiresMasterRead(false);
        sObjectField35.setNillable(true);
        sObjectField35.setCreateable(true);
        sObjectField35.setEncrypted(false);
        sObjectField35.setDigits("0");
        sObjectField35.setDependentPicklist(false);
        sObjectField35.setLabel("Account Rating");
        sObjectField35.setHighScaleNumber(false);
        sObjectField35.setDisplayLocationInDecimal(false);
        sObjectField35.setName("Rating");
        sObjectField35.setHtmlFormatted(false);
        sObjectField35.setDeprecatedAndHidden(false);
        sObjectField35.setRestrictedPicklist(false);
        sObjectField35.setNameField(false);
        sObjectField35.setCaseSensitive(false);
        sObjectField35.setPermissionable(true);
        sObjectField35.setCascadeDelete(false);
        sObjectField35.setDefaultedOnCreate(false);
        sObjectField35.setExternalId(false);
        sObjectField35.setSoapType("xsd:string");
        sObjectField35.setGroupable(true);
        sObjectField35.setCustom(false);
        sObjectField35.setScale(0);
        sObjectField35.setCalculated(false);
        sObjectField35.setRestrictedDelete(false);
        sObjectField35.setNamePointing(false);
        sObjectField35.setIdLookup(false);
        sObjectField35.setType("picklist");
        sObjectField35.setSortable(true);
        sObjectField35.setLength(40);
        sObjectField35.setPrecision(0);
        sObjectField35.setByteLength(120);
        sObjectField35.setQueryByDistance(false);
        sObjectField35.setFilterable(true);
        sObjectField35.setUpdateable(true);
        sObjectField35.setUnique(false);
        sObjectField35.setAutoNumber(false);

        final SObjectField sObjectField36 = new SObjectField();
        fields1.add(sObjectField36);

        sObjectField36.setWriteRequiresMasterRead(false);
        sObjectField36.setNillable(true);
        sObjectField36.setCreateable(true);
        sObjectField36.setEncrypted(false);
        sObjectField36.setDigits("0");
        sObjectField36.setDependentPicklist(false);
        sObjectField36.setLabel("Account Site");
        sObjectField36.setHighScaleNumber(false);
        sObjectField36.setDisplayLocationInDecimal(false);
        sObjectField36.setName("Site");
        sObjectField36.setHtmlFormatted(false);
        sObjectField36.setDeprecatedAndHidden(false);
        sObjectField36.setRestrictedPicklist(false);
        sObjectField36.setNameField(false);
        sObjectField36.setCaseSensitive(false);
        sObjectField36.setPermissionable(true);
        sObjectField36.setCascadeDelete(false);
        sObjectField36.setDefaultedOnCreate(false);
        sObjectField36.setExternalId(false);
        sObjectField36.setSoapType("xsd:string");
        sObjectField36.setGroupable(true);
        sObjectField36.setCustom(false);
        sObjectField36.setScale(0);
        sObjectField36.setCalculated(false);
        sObjectField36.setRestrictedDelete(false);
        sObjectField36.setNamePointing(false);
        sObjectField36.setIdLookup(false);
        sObjectField36.setType("string");
        sObjectField36.setSortable(true);
        sObjectField36.setLength(80);
        sObjectField36.setPrecision(0);
        sObjectField36.setByteLength(240);
        sObjectField36.setQueryByDistance(false);
        sObjectField36.setFilterable(true);
        sObjectField36.setUpdateable(true);
        sObjectField36.setUnique(false);
        sObjectField36.setAutoNumber(false);

        final SObjectField sObjectField37 = new SObjectField();
        fields1.add(sObjectField37);

        sObjectField37.setWriteRequiresMasterRead(false);
        sObjectField37.setNillable(false);
        sObjectField37.setCreateable(true);
        sObjectField37.setEncrypted(false);
        sObjectField37.setDigits("0");
        sObjectField37.setDependentPicklist(false);
        sObjectField37.setLabel("Owner ID");
        sObjectField37.setHighScaleNumber(false);
        sObjectField37.setDisplayLocationInDecimal(false);
        sObjectField37.setName("OwnerId");
        sObjectField37.setHtmlFormatted(false);
        sObjectField37.setDeprecatedAndHidden(false);
        sObjectField37.setRestrictedPicklist(false);
        sObjectField37.setNameField(false);
        sObjectField37.setCaseSensitive(false);
        sObjectField37.setPermissionable(false);
        sObjectField37.setCascadeDelete(false);
        sObjectField37.setDefaultedOnCreate(true);
        sObjectField37.setExternalId(false);
        sObjectField37.setSoapType("tns:ID");
        sObjectField37.setGroupable(true);
        sObjectField37.setCustom(false);
        sObjectField37.setScale(0);
        sObjectField37.setCalculated(false);
        sObjectField37.setRestrictedDelete(false);
        sObjectField37.setNamePointing(false);
        sObjectField37.setIdLookup(false);
        sObjectField37.setType("reference");

        final List<String> referenceTo3 = new ArrayList<>();
        sObjectField37.setReferenceTo(referenceTo3);

        referenceTo3.add("User");

        sObjectField37.setRelationshipName("Owner");
        sObjectField37.setSortable(true);
        sObjectField37.setLength(18);
        sObjectField37.setPrecision(0);
        sObjectField37.setByteLength(18);
        sObjectField37.setQueryByDistance(false);
        sObjectField37.setFilterable(true);
        sObjectField37.setUpdateable(true);
        sObjectField37.setUnique(false);
        sObjectField37.setAutoNumber(false);

        final SObjectField sObjectField38 = new SObjectField();
        fields1.add(sObjectField38);

        sObjectField38.setWriteRequiresMasterRead(false);
        sObjectField38.setNillable(false);
        sObjectField38.setCreateable(false);
        sObjectField38.setEncrypted(false);
        sObjectField38.setDigits("0");
        sObjectField38.setDependentPicklist(false);
        sObjectField38.setLabel("Created Date");
        sObjectField38.setHighScaleNumber(false);
        sObjectField38.setDisplayLocationInDecimal(false);
        sObjectField38.setName("CreatedDate");
        sObjectField38.setHtmlFormatted(false);
        sObjectField38.setDeprecatedAndHidden(false);
        sObjectField38.setRestrictedPicklist(false);
        sObjectField38.setNameField(false);
        sObjectField38.setCaseSensitive(false);
        sObjectField38.setPermissionable(false);
        sObjectField38.setCascadeDelete(false);
        sObjectField38.setDefaultedOnCreate(true);
        sObjectField38.setExternalId(false);
        sObjectField38.setSoapType("xsd:dateTime");
        sObjectField38.setGroupable(false);
        sObjectField38.setCustom(false);
        sObjectField38.setScale(0);
        sObjectField38.setCalculated(false);
        sObjectField38.setRestrictedDelete(false);
        sObjectField38.setNamePointing(false);
        sObjectField38.setIdLookup(false);
        sObjectField38.setType("datetime");
        sObjectField38.setSortable(true);
        sObjectField38.setLength(0);
        sObjectField38.setPrecision(0);
        sObjectField38.setByteLength(0);
        sObjectField38.setQueryByDistance(false);
        sObjectField38.setFilterable(true);
        sObjectField38.setUpdateable(false);
        sObjectField38.setUnique(false);
        sObjectField38.setAutoNumber(false);

        final SObjectField sObjectField39 = new SObjectField();
        fields1.add(sObjectField39);

        sObjectField39.setWriteRequiresMasterRead(false);
        sObjectField39.setNillable(false);
        sObjectField39.setCreateable(false);
        sObjectField39.setEncrypted(false);
        sObjectField39.setDigits("0");
        sObjectField39.setDependentPicklist(false);
        sObjectField39.setLabel("Created By ID");
        sObjectField39.setHighScaleNumber(false);
        sObjectField39.setDisplayLocationInDecimal(false);
        sObjectField39.setName("CreatedById");
        sObjectField39.setHtmlFormatted(false);
        sObjectField39.setDeprecatedAndHidden(false);
        sObjectField39.setRestrictedPicklist(false);
        sObjectField39.setNameField(false);
        sObjectField39.setCaseSensitive(false);
        sObjectField39.setPermissionable(false);
        sObjectField39.setCascadeDelete(false);
        sObjectField39.setDefaultedOnCreate(true);
        sObjectField39.setExternalId(false);
        sObjectField39.setSoapType("tns:ID");
        sObjectField39.setGroupable(true);
        sObjectField39.setCustom(false);
        sObjectField39.setScale(0);
        sObjectField39.setCalculated(false);
        sObjectField39.setRestrictedDelete(false);
        sObjectField39.setNamePointing(false);
        sObjectField39.setIdLookup(false);
        sObjectField39.setType("reference");

        final List<String> referenceTo4 = new ArrayList<>();
        sObjectField39.setReferenceTo(referenceTo4);

        referenceTo4.add("User");

        sObjectField39.setRelationshipName("CreatedBy");
        sObjectField39.setSortable(true);
        sObjectField39.setLength(18);
        sObjectField39.setPrecision(0);
        sObjectField39.setByteLength(18);
        sObjectField39.setQueryByDistance(false);
        sObjectField39.setFilterable(true);
        sObjectField39.setUpdateable(false);
        sObjectField39.setUnique(false);
        sObjectField39.setAutoNumber(false);

        final SObjectField sObjectField40 = new SObjectField();
        fields1.add(sObjectField40);

        sObjectField40.setWriteRequiresMasterRead(false);
        sObjectField40.setNillable(false);
        sObjectField40.setCreateable(false);
        sObjectField40.setEncrypted(false);
        sObjectField40.setDigits("0");
        sObjectField40.setDependentPicklist(false);
        sObjectField40.setLabel("Last Modified Date");
        sObjectField40.setHighScaleNumber(false);
        sObjectField40.setDisplayLocationInDecimal(false);
        sObjectField40.setName("LastModifiedDate");
        sObjectField40.setHtmlFormatted(false);
        sObjectField40.setDeprecatedAndHidden(false);
        sObjectField40.setRestrictedPicklist(false);
        sObjectField40.setNameField(false);
        sObjectField40.setCaseSensitive(false);
        sObjectField40.setPermissionable(false);
        sObjectField40.setCascadeDelete(false);
        sObjectField40.setDefaultedOnCreate(true);
        sObjectField40.setExternalId(false);
        sObjectField40.setSoapType("xsd:dateTime");
        sObjectField40.setGroupable(false);
        sObjectField40.setCustom(false);
        sObjectField40.setScale(0);
        sObjectField40.setCalculated(false);
        sObjectField40.setRestrictedDelete(false);
        sObjectField40.setNamePointing(false);
        sObjectField40.setIdLookup(false);
        sObjectField40.setType("datetime");
        sObjectField40.setSortable(true);
        sObjectField40.setLength(0);
        sObjectField40.setPrecision(0);
        sObjectField40.setByteLength(0);
        sObjectField40.setQueryByDistance(false);
        sObjectField40.setFilterable(true);
        sObjectField40.setUpdateable(false);
        sObjectField40.setUnique(false);
        sObjectField40.setAutoNumber(false);

        final SObjectField sObjectField41 = new SObjectField();
        fields1.add(sObjectField41);

        sObjectField41.setWriteRequiresMasterRead(false);
        sObjectField41.setNillable(false);
        sObjectField41.setCreateable(false);
        sObjectField41.setEncrypted(false);
        sObjectField41.setDigits("0");
        sObjectField41.setDependentPicklist(false);
        sObjectField41.setLabel("Last Modified By ID");
        sObjectField41.setHighScaleNumber(false);
        sObjectField41.setDisplayLocationInDecimal(false);
        sObjectField41.setName("LastModifiedById");
        sObjectField41.setHtmlFormatted(false);
        sObjectField41.setDeprecatedAndHidden(false);
        sObjectField41.setRestrictedPicklist(false);
        sObjectField41.setNameField(false);
        sObjectField41.setCaseSensitive(false);
        sObjectField41.setPermissionable(false);
        sObjectField41.setCascadeDelete(false);
        sObjectField41.setDefaultedOnCreate(true);
        sObjectField41.setExternalId(false);
        sObjectField41.setSoapType("tns:ID");
        sObjectField41.setGroupable(true);
        sObjectField41.setCustom(false);
        sObjectField41.setScale(0);
        sObjectField41.setCalculated(false);
        sObjectField41.setRestrictedDelete(false);
        sObjectField41.setNamePointing(false);
        sObjectField41.setIdLookup(false);
        sObjectField41.setType("reference");

        final List<String> referenceTo5 = new ArrayList<>();
        sObjectField41.setReferenceTo(referenceTo5);

        referenceTo5.add("User");

        sObjectField41.setRelationshipName("LastModifiedBy");
        sObjectField41.setSortable(true);
        sObjectField41.setLength(18);
        sObjectField41.setPrecision(0);
        sObjectField41.setByteLength(18);
        sObjectField41.setQueryByDistance(false);
        sObjectField41.setFilterable(true);
        sObjectField41.setUpdateable(false);
        sObjectField41.setUnique(false);
        sObjectField41.setAutoNumber(false);

        final SObjectField sObjectField42 = new SObjectField();
        fields1.add(sObjectField42);

        sObjectField42.setWriteRequiresMasterRead(false);
        sObjectField42.setNillable(false);
        sObjectField42.setCreateable(false);
        sObjectField42.setEncrypted(false);
        sObjectField42.setDigits("0");
        sObjectField42.setDependentPicklist(false);
        sObjectField42.setLabel("System Modstamp");
        sObjectField42.setHighScaleNumber(false);
        sObjectField42.setDisplayLocationInDecimal(false);
        sObjectField42.setName("SystemModstamp");
        sObjectField42.setHtmlFormatted(false);
        sObjectField42.setDeprecatedAndHidden(false);
        sObjectField42.setRestrictedPicklist(false);
        sObjectField42.setNameField(false);
        sObjectField42.setCaseSensitive(false);
        sObjectField42.setPermissionable(false);
        sObjectField42.setCascadeDelete(false);
        sObjectField42.setDefaultedOnCreate(true);
        sObjectField42.setExternalId(false);
        sObjectField42.setSoapType("xsd:dateTime");
        sObjectField42.setGroupable(false);
        sObjectField42.setCustom(false);
        sObjectField42.setScale(0);
        sObjectField42.setCalculated(false);
        sObjectField42.setRestrictedDelete(false);
        sObjectField42.setNamePointing(false);
        sObjectField42.setIdLookup(false);
        sObjectField42.setType("datetime");
        sObjectField42.setSortable(true);
        sObjectField42.setLength(0);
        sObjectField42.setPrecision(0);
        sObjectField42.setByteLength(0);
        sObjectField42.setQueryByDistance(false);
        sObjectField42.setFilterable(true);
        sObjectField42.setUpdateable(false);
        sObjectField42.setUnique(false);
        sObjectField42.setAutoNumber(false);

        final SObjectField sObjectField43 = new SObjectField();
        fields1.add(sObjectField43);

        sObjectField43.setWriteRequiresMasterRead(false);
        sObjectField43.setNillable(true);
        sObjectField43.setCreateable(false);
        sObjectField43.setEncrypted(false);
        sObjectField43.setDigits("0");
        sObjectField43.setDependentPicklist(false);
        sObjectField43.setLabel("Last Activity");
        sObjectField43.setHighScaleNumber(false);
        sObjectField43.setDisplayLocationInDecimal(false);
        sObjectField43.setName("LastActivityDate");
        sObjectField43.setHtmlFormatted(false);
        sObjectField43.setDeprecatedAndHidden(false);
        sObjectField43.setRestrictedPicklist(false);
        sObjectField43.setNameField(false);
        sObjectField43.setCaseSensitive(false);
        sObjectField43.setPermissionable(false);
        sObjectField43.setCascadeDelete(false);
        sObjectField43.setDefaultedOnCreate(false);
        sObjectField43.setExternalId(false);
        sObjectField43.setSoapType("xsd:date");
        sObjectField43.setGroupable(true);
        sObjectField43.setCustom(false);
        sObjectField43.setScale(0);
        sObjectField43.setCalculated(false);
        sObjectField43.setRestrictedDelete(false);
        sObjectField43.setNamePointing(false);
        sObjectField43.setIdLookup(false);
        sObjectField43.setType("date");
        sObjectField43.setSortable(true);
        sObjectField43.setLength(0);
        sObjectField43.setPrecision(0);
        sObjectField43.setByteLength(0);
        sObjectField43.setQueryByDistance(false);
        sObjectField43.setFilterable(true);
        sObjectField43.setUpdateable(false);
        sObjectField43.setUnique(false);
        sObjectField43.setAutoNumber(false);

        final SObjectField sObjectField44 = new SObjectField();
        fields1.add(sObjectField44);

        sObjectField44.setWriteRequiresMasterRead(false);
        sObjectField44.setNillable(true);
        sObjectField44.setCreateable(false);
        sObjectField44.setEncrypted(false);
        sObjectField44.setDigits("0");
        sObjectField44.setDependentPicklist(false);
        sObjectField44.setLabel("Last Viewed Date");
        sObjectField44.setHighScaleNumber(false);
        sObjectField44.setDisplayLocationInDecimal(false);
        sObjectField44.setName("LastViewedDate");
        sObjectField44.setHtmlFormatted(false);
        sObjectField44.setDeprecatedAndHidden(false);
        sObjectField44.setRestrictedPicklist(false);
        sObjectField44.setNameField(false);
        sObjectField44.setCaseSensitive(false);
        sObjectField44.setPermissionable(false);
        sObjectField44.setCascadeDelete(false);
        sObjectField44.setDefaultedOnCreate(false);
        sObjectField44.setExternalId(false);
        sObjectField44.setSoapType("xsd:dateTime");
        sObjectField44.setGroupable(false);
        sObjectField44.setCustom(false);
        sObjectField44.setScale(0);
        sObjectField44.setCalculated(false);
        sObjectField44.setRestrictedDelete(false);
        sObjectField44.setNamePointing(false);
        sObjectField44.setIdLookup(false);
        sObjectField44.setType("datetime");
        sObjectField44.setSortable(true);
        sObjectField44.setLength(0);
        sObjectField44.setPrecision(0);
        sObjectField44.setByteLength(0);
        sObjectField44.setQueryByDistance(false);
        sObjectField44.setFilterable(true);
        sObjectField44.setUpdateable(false);
        sObjectField44.setUnique(false);
        sObjectField44.setAutoNumber(false);

        final SObjectField sObjectField45 = new SObjectField();
        fields1.add(sObjectField45);

        sObjectField45.setWriteRequiresMasterRead(false);
        sObjectField45.setNillable(true);
        sObjectField45.setCreateable(false);
        sObjectField45.setEncrypted(false);
        sObjectField45.setDigits("0");
        sObjectField45.setDependentPicklist(false);
        sObjectField45.setLabel("Last Referenced Date");
        sObjectField45.setHighScaleNumber(false);
        sObjectField45.setDisplayLocationInDecimal(false);
        sObjectField45.setName("LastReferencedDate");
        sObjectField45.setHtmlFormatted(false);
        sObjectField45.setDeprecatedAndHidden(false);
        sObjectField45.setRestrictedPicklist(false);
        sObjectField45.setNameField(false);
        sObjectField45.setCaseSensitive(false);
        sObjectField45.setPermissionable(false);
        sObjectField45.setCascadeDelete(false);
        sObjectField45.setDefaultedOnCreate(false);
        sObjectField45.setExternalId(false);
        sObjectField45.setSoapType("xsd:dateTime");
        sObjectField45.setGroupable(false);
        sObjectField45.setCustom(false);
        sObjectField45.setScale(0);
        sObjectField45.setCalculated(false);
        sObjectField45.setRestrictedDelete(false);
        sObjectField45.setNamePointing(false);
        sObjectField45.setIdLookup(false);
        sObjectField45.setType("datetime");
        sObjectField45.setSortable(true);
        sObjectField45.setLength(0);
        sObjectField45.setPrecision(0);
        sObjectField45.setByteLength(0);
        sObjectField45.setQueryByDistance(false);
        sObjectField45.setFilterable(true);
        sObjectField45.setUpdateable(false);
        sObjectField45.setUnique(false);
        sObjectField45.setAutoNumber(false);

        final SObjectField sObjectField46 = new SObjectField();
        fields1.add(sObjectField46);

        sObjectField46.setWriteRequiresMasterRead(false);
        sObjectField46.setNillable(true);
        sObjectField46.setCreateable(true);
        sObjectField46.setEncrypted(false);
        sObjectField46.setDigits("0");
        sObjectField46.setDependentPicklist(false);
        sObjectField46.setLabel("Data.com Key");
        sObjectField46.setHighScaleNumber(false);
        sObjectField46.setDisplayLocationInDecimal(false);
        sObjectField46.setName("Jigsaw");
        sObjectField46.setHtmlFormatted(false);
        sObjectField46.setDeprecatedAndHidden(false);
        sObjectField46.setRestrictedPicklist(false);
        sObjectField46.setNameField(false);
        sObjectField46.setCaseSensitive(false);
        sObjectField46.setPermissionable(true);
        sObjectField46.setCascadeDelete(false);
        sObjectField46.setDefaultedOnCreate(false);
        sObjectField46.setExternalId(false);
        sObjectField46.setSoapType("xsd:string");
        sObjectField46.setGroupable(true);
        sObjectField46.setCustom(false);
        sObjectField46.setScale(0);
        sObjectField46.setCalculated(false);
        sObjectField46.setRestrictedDelete(false);
        sObjectField46.setNamePointing(false);
        sObjectField46.setIdLookup(false);
        sObjectField46.setType("string");
        sObjectField46.setSortable(true);
        sObjectField46.setLength(20);
        sObjectField46.setPrecision(0);
        sObjectField46.setByteLength(60);
        sObjectField46.setQueryByDistance(false);
        sObjectField46.setFilterable(true);
        sObjectField46.setUpdateable(true);
        sObjectField46.setUnique(false);
        sObjectField46.setAutoNumber(false);

        final SObjectField sObjectField47 = new SObjectField();
        fields1.add(sObjectField47);

        sObjectField47.setWriteRequiresMasterRead(false);
        sObjectField47.setNillable(true);
        sObjectField47.setCreateable(false);
        sObjectField47.setEncrypted(false);
        sObjectField47.setDigits("0");
        sObjectField47.setDependentPicklist(false);
        sObjectField47.setLabel("Jigsaw Company ID");
        sObjectField47.setHighScaleNumber(false);
        sObjectField47.setDisplayLocationInDecimal(false);
        sObjectField47.setName("JigsawCompanyId");
        sObjectField47.setHtmlFormatted(false);
        sObjectField47.setDeprecatedAndHidden(false);
        sObjectField47.setRestrictedPicklist(false);
        sObjectField47.setNameField(false);
        sObjectField47.setCaseSensitive(false);
        sObjectField47.setPermissionable(false);
        sObjectField47.setCascadeDelete(false);
        sObjectField47.setDefaultedOnCreate(false);
        sObjectField47.setExternalId(false);
        sObjectField47.setSoapType("xsd:string");
        sObjectField47.setGroupable(true);
        sObjectField47.setCustom(false);
        sObjectField47.setScale(0);
        sObjectField47.setCalculated(false);
        sObjectField47.setRestrictedDelete(false);
        sObjectField47.setNamePointing(false);
        sObjectField47.setIdLookup(false);
        sObjectField47.setType("string");
        sObjectField47.setRelationshipName("JigsawCompany");
        sObjectField47.setSortable(true);
        sObjectField47.setLength(20);
        sObjectField47.setPrecision(0);
        sObjectField47.setByteLength(60);
        sObjectField47.setQueryByDistance(false);
        sObjectField47.setFilterable(true);
        sObjectField47.setUpdateable(false);
        sObjectField47.setUnique(false);
        sObjectField47.setAutoNumber(false);

        final SObjectField sObjectField48 = new SObjectField();
        fields1.add(sObjectField48);

        sObjectField48.setWriteRequiresMasterRead(false);
        sObjectField48.setNillable(true);
        sObjectField48.setCreateable(true);
        sObjectField48.setEncrypted(false);
        sObjectField48.setDigits("0");
        sObjectField48.setDependentPicklist(false);
        sObjectField48.setLabel("Clean Status");
        sObjectField48.setHighScaleNumber(false);
        sObjectField48.setDisplayLocationInDecimal(false);
        sObjectField48.setName("CleanStatus");
        sObjectField48.setHtmlFormatted(false);
        sObjectField48.setDeprecatedAndHidden(false);
        sObjectField48.setRestrictedPicklist(true);
        sObjectField48.setNameField(false);
        sObjectField48.setCaseSensitive(false);
        sObjectField48.setPermissionable(true);
        sObjectField48.setCascadeDelete(false);
        sObjectField48.setDefaultedOnCreate(false);
        sObjectField48.setExternalId(false);
        sObjectField48.setSoapType("xsd:string");
        sObjectField48.setGroupable(true);
        sObjectField48.setCustom(false);
        sObjectField48.setScale(0);
        sObjectField48.setCalculated(false);
        sObjectField48.setRestrictedDelete(false);
        sObjectField48.setNamePointing(false);
        sObjectField48.setIdLookup(false);
        sObjectField48.setType("picklist");
        sObjectField48.setSortable(true);
        sObjectField48.setLength(40);
        sObjectField48.setPrecision(0);
        sObjectField48.setByteLength(120);
        sObjectField48.setQueryByDistance(false);
        sObjectField48.setFilterable(true);
        sObjectField48.setUpdateable(true);
        sObjectField48.setUnique(false);
        sObjectField48.setAutoNumber(false);

        final SObjectField sObjectField49 = new SObjectField();
        fields1.add(sObjectField49);

        sObjectField49.setWriteRequiresMasterRead(false);
        sObjectField49.setNillable(true);
        sObjectField49.setCreateable(true);
        sObjectField49.setEncrypted(false);
        sObjectField49.setDigits("0");
        sObjectField49.setDependentPicklist(false);
        sObjectField49.setLabel("Account Source");
        sObjectField49.setHighScaleNumber(false);
        sObjectField49.setDisplayLocationInDecimal(false);
        sObjectField49.setName("AccountSource");
        sObjectField49.setHtmlFormatted(false);
        sObjectField49.setDeprecatedAndHidden(false);
        sObjectField49.setRestrictedPicklist(false);
        sObjectField49.setNameField(false);
        sObjectField49.setCaseSensitive(false);
        sObjectField49.setPermissionable(true);
        sObjectField49.setCascadeDelete(false);
        sObjectField49.setDefaultedOnCreate(false);
        sObjectField49.setExternalId(false);
        sObjectField49.setSoapType("xsd:string");
        sObjectField49.setGroupable(true);
        sObjectField49.setCustom(false);
        sObjectField49.setScale(0);
        sObjectField49.setCalculated(false);
        sObjectField49.setRestrictedDelete(false);
        sObjectField49.setNamePointing(false);
        sObjectField49.setIdLookup(false);
        sObjectField49.setType("picklist");
        sObjectField49.setSortable(true);
        sObjectField49.setLength(40);
        sObjectField49.setPrecision(0);
        sObjectField49.setByteLength(120);
        sObjectField49.setQueryByDistance(false);
        sObjectField49.setFilterable(true);
        sObjectField49.setUpdateable(true);
        sObjectField49.setUnique(false);
        sObjectField49.setAutoNumber(false);

        final SObjectField sObjectField50 = new SObjectField();
        fields1.add(sObjectField50);

        sObjectField50.setWriteRequiresMasterRead(false);
        sObjectField50.setNillable(true);
        sObjectField50.setCreateable(true);
        sObjectField50.setEncrypted(false);
        sObjectField50.setDigits("0");
        sObjectField50.setDependentPicklist(false);
        sObjectField50.setLabel("D-U-N-S Number");
        sObjectField50.setHighScaleNumber(false);
        sObjectField50.setDisplayLocationInDecimal(false);
        sObjectField50.setName("DunsNumber");
        sObjectField50.setHtmlFormatted(false);
        sObjectField50.setDeprecatedAndHidden(false);
        sObjectField50.setRestrictedPicklist(false);
        sObjectField50.setNameField(false);
        sObjectField50.setCaseSensitive(false);
        sObjectField50.setPermissionable(true);
        sObjectField50.setCascadeDelete(false);
        sObjectField50.setDefaultedOnCreate(false);
        sObjectField50.setExternalId(false);
        sObjectField50.setSoapType("xsd:string");
        sObjectField50.setGroupable(true);
        sObjectField50.setCustom(false);
        sObjectField50.setScale(0);
        sObjectField50.setCalculated(false);
        sObjectField50.setRestrictedDelete(false);
        sObjectField50.setNamePointing(false);
        sObjectField50.setIdLookup(false);
        sObjectField50.setType("string");
        sObjectField50.setSortable(true);
        sObjectField50.setLength(9);
        sObjectField50.setPrecision(0);
        sObjectField50.setByteLength(27);
        sObjectField50.setQueryByDistance(false);
        sObjectField50.setFilterable(true);
        sObjectField50.setUpdateable(true);
        sObjectField50.setUnique(false);
        sObjectField50.setAutoNumber(false);

        final SObjectField sObjectField51 = new SObjectField();
        fields1.add(sObjectField51);

        sObjectField51.setWriteRequiresMasterRead(false);
        sObjectField51.setNillable(true);
        sObjectField51.setCreateable(true);
        sObjectField51.setEncrypted(false);
        sObjectField51.setDigits("0");
        sObjectField51.setDependentPicklist(false);
        sObjectField51.setLabel("Tradestyle");
        sObjectField51.setHighScaleNumber(false);
        sObjectField51.setDisplayLocationInDecimal(false);
        sObjectField51.setName("Tradestyle");
        sObjectField51.setHtmlFormatted(false);
        sObjectField51.setDeprecatedAndHidden(false);
        sObjectField51.setRestrictedPicklist(false);
        sObjectField51.setNameField(false);
        sObjectField51.setCaseSensitive(false);
        sObjectField51.setPermissionable(true);
        sObjectField51.setCascadeDelete(false);
        sObjectField51.setDefaultedOnCreate(false);
        sObjectField51.setExternalId(false);
        sObjectField51.setSoapType("xsd:string");
        sObjectField51.setGroupable(true);
        sObjectField51.setCustom(false);
        sObjectField51.setScale(0);
        sObjectField51.setCalculated(false);
        sObjectField51.setRestrictedDelete(false);
        sObjectField51.setNamePointing(false);
        sObjectField51.setIdLookup(false);
        sObjectField51.setType("string");
        sObjectField51.setSortable(true);
        sObjectField51.setLength(255);
        sObjectField51.setPrecision(0);
        sObjectField51.setByteLength(765);
        sObjectField51.setQueryByDistance(false);
        sObjectField51.setFilterable(true);
        sObjectField51.setUpdateable(true);
        sObjectField51.setUnique(false);
        sObjectField51.setAutoNumber(false);

        final SObjectField sObjectField52 = new SObjectField();
        fields1.add(sObjectField52);

        sObjectField52.setWriteRequiresMasterRead(false);
        sObjectField52.setNillable(true);
        sObjectField52.setCreateable(true);
        sObjectField52.setEncrypted(false);
        sObjectField52.setDigits("0");
        sObjectField52.setDependentPicklist(false);
        sObjectField52.setLabel("NAICS Code");
        sObjectField52.setHighScaleNumber(false);
        sObjectField52.setDisplayLocationInDecimal(false);
        sObjectField52.setName("NaicsCode");
        sObjectField52.setHtmlFormatted(false);
        sObjectField52.setDeprecatedAndHidden(false);
        sObjectField52.setRestrictedPicklist(false);
        sObjectField52.setNameField(false);
        sObjectField52.setCaseSensitive(false);
        sObjectField52.setPermissionable(true);
        sObjectField52.setCascadeDelete(false);
        sObjectField52.setDefaultedOnCreate(false);
        sObjectField52.setExternalId(false);
        sObjectField52.setSoapType("xsd:string");
        sObjectField52.setGroupable(true);
        sObjectField52.setCustom(false);
        sObjectField52.setScale(0);
        sObjectField52.setCalculated(false);
        sObjectField52.setRestrictedDelete(false);
        sObjectField52.setNamePointing(false);
        sObjectField52.setIdLookup(false);
        sObjectField52.setType("string");
        sObjectField52.setSortable(true);
        sObjectField52.setLength(8);
        sObjectField52.setPrecision(0);
        sObjectField52.setByteLength(24);
        sObjectField52.setQueryByDistance(false);
        sObjectField52.setFilterable(true);
        sObjectField52.setUpdateable(true);
        sObjectField52.setUnique(false);
        sObjectField52.setAutoNumber(false);

        final SObjectField sObjectField53 = new SObjectField();
        fields1.add(sObjectField53);

        sObjectField53.setWriteRequiresMasterRead(false);
        sObjectField53.setNillable(true);
        sObjectField53.setCreateable(true);
        sObjectField53.setEncrypted(false);
        sObjectField53.setDigits("0");
        sObjectField53.setDependentPicklist(false);
        sObjectField53.setLabel("NAICS Description");
        sObjectField53.setHighScaleNumber(false);
        sObjectField53.setDisplayLocationInDecimal(false);
        sObjectField53.setName("NaicsDesc");
        sObjectField53.setHtmlFormatted(false);
        sObjectField53.setDeprecatedAndHidden(false);
        sObjectField53.setRestrictedPicklist(false);
        sObjectField53.setNameField(false);
        sObjectField53.setCaseSensitive(false);
        sObjectField53.setPermissionable(true);
        sObjectField53.setCascadeDelete(false);
        sObjectField53.setDefaultedOnCreate(false);
        sObjectField53.setExternalId(false);
        sObjectField53.setSoapType("xsd:string");
        sObjectField53.setGroupable(true);
        sObjectField53.setCustom(false);
        sObjectField53.setScale(0);
        sObjectField53.setCalculated(false);
        sObjectField53.setRestrictedDelete(false);
        sObjectField53.setNamePointing(false);
        sObjectField53.setIdLookup(false);
        sObjectField53.setType("string");
        sObjectField53.setSortable(true);
        sObjectField53.setLength(120);
        sObjectField53.setPrecision(0);
        sObjectField53.setByteLength(360);
        sObjectField53.setQueryByDistance(false);
        sObjectField53.setFilterable(true);
        sObjectField53.setUpdateable(true);
        sObjectField53.setUnique(false);
        sObjectField53.setAutoNumber(false);

        final SObjectField sObjectField54 = new SObjectField();
        fields1.add(sObjectField54);

        sObjectField54.setWriteRequiresMasterRead(false);
        sObjectField54.setNillable(true);
        sObjectField54.setCreateable(true);
        sObjectField54.setEncrypted(false);
        sObjectField54.setDigits("0");
        sObjectField54.setDependentPicklist(false);
        sObjectField54.setLabel("Year Started");
        sObjectField54.setHighScaleNumber(false);
        sObjectField54.setDisplayLocationInDecimal(false);
        sObjectField54.setName("YearStarted");
        sObjectField54.setHtmlFormatted(false);
        sObjectField54.setDeprecatedAndHidden(false);
        sObjectField54.setRestrictedPicklist(false);
        sObjectField54.setNameField(false);
        sObjectField54.setCaseSensitive(false);
        sObjectField54.setPermissionable(true);
        sObjectField54.setCascadeDelete(false);
        sObjectField54.setDefaultedOnCreate(false);
        sObjectField54.setExternalId(false);
        sObjectField54.setSoapType("xsd:string");
        sObjectField54.setGroupable(true);
        sObjectField54.setCustom(false);
        sObjectField54.setScale(0);
        sObjectField54.setCalculated(false);
        sObjectField54.setRestrictedDelete(false);
        sObjectField54.setNamePointing(false);
        sObjectField54.setIdLookup(false);
        sObjectField54.setType("string");
        sObjectField54.setSortable(true);
        sObjectField54.setLength(4);
        sObjectField54.setPrecision(0);
        sObjectField54.setByteLength(12);
        sObjectField54.setQueryByDistance(false);
        sObjectField54.setFilterable(true);
        sObjectField54.setUpdateable(true);
        sObjectField54.setUnique(false);
        sObjectField54.setAutoNumber(false);

        final SObjectField sObjectField55 = new SObjectField();
        fields1.add(sObjectField55);

        sObjectField55.setWriteRequiresMasterRead(false);
        sObjectField55.setNillable(true);
        sObjectField55.setCreateable(true);
        sObjectField55.setEncrypted(false);
        sObjectField55.setDigits("0");
        sObjectField55.setDependentPicklist(false);
        sObjectField55.setLabel("SIC Description");
        sObjectField55.setHighScaleNumber(false);
        sObjectField55.setDisplayLocationInDecimal(false);
        sObjectField55.setName("SicDesc");
        sObjectField55.setHtmlFormatted(false);
        sObjectField55.setDeprecatedAndHidden(false);
        sObjectField55.setRestrictedPicklist(false);
        sObjectField55.setNameField(false);
        sObjectField55.setCaseSensitive(false);
        sObjectField55.setPermissionable(true);
        sObjectField55.setCascadeDelete(false);
        sObjectField55.setDefaultedOnCreate(false);
        sObjectField55.setExternalId(false);
        sObjectField55.setSoapType("xsd:string");
        sObjectField55.setGroupable(true);
        sObjectField55.setCustom(false);
        sObjectField55.setScale(0);
        sObjectField55.setCalculated(false);
        sObjectField55.setRestrictedDelete(false);
        sObjectField55.setNamePointing(false);
        sObjectField55.setIdLookup(false);
        sObjectField55.setType("string");
        sObjectField55.setSortable(true);
        sObjectField55.setLength(80);
        sObjectField55.setPrecision(0);
        sObjectField55.setByteLength(240);
        sObjectField55.setQueryByDistance(false);
        sObjectField55.setFilterable(true);
        sObjectField55.setUpdateable(true);
        sObjectField55.setUnique(false);
        sObjectField55.setAutoNumber(false);

        final SObjectField sObjectField56 = new SObjectField();
        fields1.add(sObjectField56);

        sObjectField56.setWriteRequiresMasterRead(false);
        sObjectField56.setNillable(true);
        sObjectField56.setCreateable(true);
        sObjectField56.setEncrypted(false);
        sObjectField56.setDigits("0");
        sObjectField56.setDependentPicklist(false);
        sObjectField56.setLabel("D&B Company ID");
        sObjectField56.setHighScaleNumber(false);
        sObjectField56.setDisplayLocationInDecimal(false);
        sObjectField56.setName("DandbCompanyId");
        sObjectField56.setHtmlFormatted(false);
        sObjectField56.setDeprecatedAndHidden(false);
        sObjectField56.setRestrictedPicklist(false);
        sObjectField56.setNameField(false);
        sObjectField56.setCaseSensitive(false);
        sObjectField56.setPermissionable(true);
        sObjectField56.setCascadeDelete(false);
        sObjectField56.setDefaultedOnCreate(false);
        sObjectField56.setExternalId(false);
        sObjectField56.setSoapType("tns:ID");
        sObjectField56.setGroupable(true);
        sObjectField56.setCustom(false);
        sObjectField56.setScale(0);
        sObjectField56.setCalculated(false);
        sObjectField56.setRestrictedDelete(false);
        sObjectField56.setNamePointing(false);
        sObjectField56.setIdLookup(false);
        sObjectField56.setType("reference");

        final List<String> referenceTo6 = new ArrayList<>();
        sObjectField56.setReferenceTo(referenceTo6);

        referenceTo6.add("DandBCompany");

        sObjectField56.setRelationshipName("DandbCompany");
        sObjectField56.setSortable(true);
        sObjectField56.setLength(18);
        sObjectField56.setPrecision(0);
        sObjectField56.setByteLength(18);
        sObjectField56.setQueryByDistance(false);
        sObjectField56.setFilterable(true);
        sObjectField56.setUpdateable(true);
        sObjectField56.setUnique(false);
        sObjectField56.setAutoNumber(false);

        final SObjectField sObjectField57 = new SObjectField();
        fields1.add(sObjectField57);

        sObjectField57.setWriteRequiresMasterRead(false);
        sObjectField57.setNillable(true);
        sObjectField57.setCreateable(true);
        sObjectField57.setEncrypted(false);
        sObjectField57.setDigits("0");
        sObjectField57.setDependentPicklist(false);
        sObjectField57.setLabel("Customer Priority");
        sObjectField57.setHighScaleNumber(false);
        sObjectField57.setDisplayLocationInDecimal(false);
        sObjectField57.setName("CustomerPriority__c");
        sObjectField57.setHtmlFormatted(false);
        sObjectField57.setDeprecatedAndHidden(false);
        sObjectField57.setRestrictedPicklist(false);
        sObjectField57.setNameField(false);
        sObjectField57.setCaseSensitive(false);
        sObjectField57.setPermissionable(true);
        sObjectField57.setCascadeDelete(false);
        sObjectField57.setDefaultedOnCreate(false);
        sObjectField57.setExternalId(false);
        sObjectField57.setSoapType("xsd:string");
        sObjectField57.setGroupable(true);
        sObjectField57.setCustom(true);
        sObjectField57.setScale(0);
        sObjectField57.setCalculated(false);
        sObjectField57.setRestrictedDelete(false);
        sObjectField57.setNamePointing(false);
        sObjectField57.setIdLookup(false);
        sObjectField57.setType("picklist");
        sObjectField57.setSortable(true);
        sObjectField57.setLength(255);
        sObjectField57.setPrecision(0);
        sObjectField57.setByteLength(765);
        sObjectField57.setQueryByDistance(false);
        sObjectField57.setFilterable(true);
        sObjectField57.setUpdateable(true);
        sObjectField57.setUnique(false);
        sObjectField57.setAutoNumber(false);

        final SObjectField sObjectField58 = new SObjectField();
        fields1.add(sObjectField58);

        sObjectField58.setWriteRequiresMasterRead(false);
        sObjectField58.setNillable(true);
        sObjectField58.setCreateable(true);
        sObjectField58.setEncrypted(false);
        sObjectField58.setDigits("0");
        sObjectField58.setDependentPicklist(false);
        sObjectField58.setLabel("SLA");
        sObjectField58.setHighScaleNumber(false);
        sObjectField58.setDisplayLocationInDecimal(false);
        sObjectField58.setName("SLA__c");
        sObjectField58.setHtmlFormatted(false);
        sObjectField58.setDeprecatedAndHidden(false);
        sObjectField58.setRestrictedPicklist(false);
        sObjectField58.setNameField(false);
        sObjectField58.setCaseSensitive(false);
        sObjectField58.setPermissionable(true);
        sObjectField58.setCascadeDelete(false);
        sObjectField58.setDefaultedOnCreate(false);
        sObjectField58.setExternalId(false);
        sObjectField58.setSoapType("xsd:string");
        sObjectField58.setGroupable(true);
        sObjectField58.setCustom(true);
        sObjectField58.setScale(0);
        sObjectField58.setCalculated(false);
        sObjectField58.setRestrictedDelete(false);
        sObjectField58.setNamePointing(false);
        sObjectField58.setIdLookup(false);
        sObjectField58.setType("picklist");
        sObjectField58.setSortable(true);
        sObjectField58.setLength(255);
        sObjectField58.setPrecision(0);
        sObjectField58.setByteLength(765);
        sObjectField58.setQueryByDistance(false);
        sObjectField58.setFilterable(true);
        sObjectField58.setUpdateable(true);
        sObjectField58.setUnique(false);
        sObjectField58.setAutoNumber(false);

        final SObjectField sObjectField59 = new SObjectField();
        fields1.add(sObjectField59);

        sObjectField59.setWriteRequiresMasterRead(false);
        sObjectField59.setNillable(true);
        sObjectField59.setCreateable(true);
        sObjectField59.setEncrypted(false);
        sObjectField59.setDigits("0");
        sObjectField59.setDependentPicklist(false);
        sObjectField59.setLabel("Active");
        sObjectField59.setHighScaleNumber(false);
        sObjectField59.setDisplayLocationInDecimal(false);
        sObjectField59.setName("Active__c");
        sObjectField59.setHtmlFormatted(false);
        sObjectField59.setDeprecatedAndHidden(false);
        sObjectField59.setRestrictedPicklist(false);
        sObjectField59.setNameField(false);
        sObjectField59.setCaseSensitive(false);
        sObjectField59.setPermissionable(true);
        sObjectField59.setCascadeDelete(false);
        sObjectField59.setDefaultedOnCreate(false);
        sObjectField59.setExternalId(false);
        sObjectField59.setSoapType("xsd:string");
        sObjectField59.setGroupable(true);
        sObjectField59.setCustom(true);
        sObjectField59.setScale(0);
        sObjectField59.setCalculated(false);
        sObjectField59.setRestrictedDelete(false);
        sObjectField59.setNamePointing(false);
        sObjectField59.setIdLookup(false);
        sObjectField59.setType("picklist");
        sObjectField59.setSortable(true);
        sObjectField59.setLength(255);
        sObjectField59.setPrecision(0);
        sObjectField59.setByteLength(765);
        sObjectField59.setQueryByDistance(false);
        sObjectField59.setFilterable(true);
        sObjectField59.setUpdateable(true);
        sObjectField59.setUnique(false);
        sObjectField59.setAutoNumber(false);

        final SObjectField sObjectField60 = new SObjectField();
        fields1.add(sObjectField60);

        sObjectField60.setWriteRequiresMasterRead(false);
        sObjectField60.setNillable(true);
        sObjectField60.setCreateable(true);
        sObjectField60.setEncrypted(false);
        sObjectField60.setDigits("0");
        sObjectField60.setDependentPicklist(false);
        sObjectField60.setLabel("Number of Locations");
        sObjectField60.setHighScaleNumber(false);
        sObjectField60.setDisplayLocationInDecimal(false);
        sObjectField60.setName("NumberofLocations__c");
        sObjectField60.setHtmlFormatted(false);
        sObjectField60.setDeprecatedAndHidden(false);
        sObjectField60.setRestrictedPicklist(false);
        sObjectField60.setNameField(false);
        sObjectField60.setCaseSensitive(false);
        sObjectField60.setPermissionable(true);
        sObjectField60.setCascadeDelete(false);
        sObjectField60.setDefaultedOnCreate(false);
        sObjectField60.setExternalId(false);
        sObjectField60.setSoapType("xsd:double");
        sObjectField60.setGroupable(false);
        sObjectField60.setCustom(true);
        sObjectField60.setScale(0);
        sObjectField60.setCalculated(false);
        sObjectField60.setRestrictedDelete(false);
        sObjectField60.setNamePointing(false);
        sObjectField60.setIdLookup(false);
        sObjectField60.setType("double");
        sObjectField60.setSortable(true);
        sObjectField60.setLength(0);
        sObjectField60.setPrecision(3);
        sObjectField60.setByteLength(0);
        sObjectField60.setQueryByDistance(false);
        sObjectField60.setFilterable(true);
        sObjectField60.setUpdateable(true);
        sObjectField60.setUnique(false);
        sObjectField60.setAutoNumber(false);

        final SObjectField sObjectField61 = new SObjectField();
        fields1.add(sObjectField61);

        sObjectField61.setWriteRequiresMasterRead(false);
        sObjectField61.setNillable(true);
        sObjectField61.setCreateable(true);
        sObjectField61.setEncrypted(false);
        sObjectField61.setDigits("0");
        sObjectField61.setDependentPicklist(false);
        sObjectField61.setLabel("Upsell Opportunity");
        sObjectField61.setHighScaleNumber(false);
        sObjectField61.setDisplayLocationInDecimal(false);
        sObjectField61.setName("UpsellOpportunity__c");
        sObjectField61.setHtmlFormatted(false);
        sObjectField61.setDeprecatedAndHidden(false);
        sObjectField61.setRestrictedPicklist(false);
        sObjectField61.setNameField(false);
        sObjectField61.setCaseSensitive(false);
        sObjectField61.setPermissionable(true);
        sObjectField61.setCascadeDelete(false);
        sObjectField61.setDefaultedOnCreate(false);
        sObjectField61.setExternalId(false);
        sObjectField61.setSoapType("xsd:string");
        sObjectField61.setGroupable(true);
        sObjectField61.setCustom(true);
        sObjectField61.setScale(0);
        sObjectField61.setCalculated(false);
        sObjectField61.setRestrictedDelete(false);
        sObjectField61.setNamePointing(false);
        sObjectField61.setIdLookup(false);
        sObjectField61.setType("picklist");
        sObjectField61.setSortable(true);
        sObjectField61.setLength(255);
        sObjectField61.setPrecision(0);
        sObjectField61.setByteLength(765);
        sObjectField61.setQueryByDistance(false);
        sObjectField61.setFilterable(true);
        sObjectField61.setUpdateable(true);
        sObjectField61.setUnique(false);
        sObjectField61.setAutoNumber(false);

        final SObjectField sObjectField62 = new SObjectField();
        fields1.add(sObjectField62);

        sObjectField62.setWriteRequiresMasterRead(false);
        sObjectField62.setNillable(true);
        sObjectField62.setCreateable(true);
        sObjectField62.setEncrypted(false);
        sObjectField62.setDigits("0");
        sObjectField62.setDependentPicklist(false);
        sObjectField62.setLabel("SLA Serial Number");
        sObjectField62.setHighScaleNumber(false);
        sObjectField62.setDisplayLocationInDecimal(false);
        sObjectField62.setName("SLASerialNumber__c");
        sObjectField62.setHtmlFormatted(false);
        sObjectField62.setDeprecatedAndHidden(false);
        sObjectField62.setRestrictedPicklist(false);
        sObjectField62.setNameField(false);
        sObjectField62.setCaseSensitive(false);
        sObjectField62.setPermissionable(true);
        sObjectField62.setCascadeDelete(false);
        sObjectField62.setDefaultedOnCreate(false);
        sObjectField62.setExternalId(false);
        sObjectField62.setSoapType("xsd:string");
        sObjectField62.setGroupable(true);
        sObjectField62.setCustom(true);
        sObjectField62.setScale(0);
        sObjectField62.setCalculated(false);
        sObjectField62.setRestrictedDelete(false);
        sObjectField62.setNamePointing(false);
        sObjectField62.setIdLookup(false);
        sObjectField62.setType("string");
        sObjectField62.setSortable(true);
        sObjectField62.setLength(10);
        sObjectField62.setPrecision(0);
        sObjectField62.setByteLength(30);
        sObjectField62.setQueryByDistance(false);
        sObjectField62.setFilterable(true);
        sObjectField62.setUpdateable(true);
        sObjectField62.setUnique(false);
        sObjectField62.setAutoNumber(false);

        final SObjectField sObjectField63 = new SObjectField();
        fields1.add(sObjectField63);

        sObjectField63.setWriteRequiresMasterRead(false);
        sObjectField63.setNillable(true);
        sObjectField63.setCreateable(true);
        sObjectField63.setEncrypted(false);
        sObjectField63.setDigits("0");
        sObjectField63.setDependentPicklist(false);
        sObjectField63.setLabel("SLA Expiration Date");
        sObjectField63.setHighScaleNumber(false);
        sObjectField63.setDisplayLocationInDecimal(false);
        sObjectField63.setName("SLAExpirationDate__c");
        sObjectField63.setHtmlFormatted(false);
        sObjectField63.setDeprecatedAndHidden(false);
        sObjectField63.setRestrictedPicklist(false);
        sObjectField63.setNameField(false);
        sObjectField63.setCaseSensitive(false);
        sObjectField63.setPermissionable(true);
        sObjectField63.setCascadeDelete(false);
        sObjectField63.setDefaultedOnCreate(false);
        sObjectField63.setExternalId(false);
        sObjectField63.setSoapType("xsd:date");
        sObjectField63.setGroupable(true);
        sObjectField63.setCustom(true);
        sObjectField63.setScale(0);
        sObjectField63.setCalculated(false);
        sObjectField63.setRestrictedDelete(false);
        sObjectField63.setNamePointing(false);
        sObjectField63.setIdLookup(false);
        sObjectField63.setType("date");
        sObjectField63.setSortable(true);
        sObjectField63.setLength(0);
        sObjectField63.setPrecision(0);
        sObjectField63.setByteLength(0);
        sObjectField63.setQueryByDistance(false);
        sObjectField63.setFilterable(true);
        sObjectField63.setUpdateable(true);
        sObjectField63.setUnique(false);
        sObjectField63.setAutoNumber(false);

        final SObjectField sObjectField64 = new SObjectField();
        fields1.add(sObjectField64);

        sObjectField64.setWriteRequiresMasterRead(false);
        sObjectField64.setNillable(true);
        sObjectField64.setCreateable(true);
        sObjectField64.setEncrypted(false);
        sObjectField64.setDigits("0");
        sObjectField64.setDependentPicklist(false);
        sObjectField64.setLabel("Shipping_Location (Latitude)");
        sObjectField64.setHighScaleNumber(false);
        sObjectField64.setDisplayLocationInDecimal(false);
        sObjectField64.setName("Shipping_Location__Latitude__s");
        sObjectField64.setHtmlFormatted(false);
        sObjectField64.setDeprecatedAndHidden(false);
        sObjectField64.setRestrictedPicklist(false);
        sObjectField64.setNameField(false);
        sObjectField64.setCaseSensitive(false);
        sObjectField64.setPermissionable(true);
        sObjectField64.setCascadeDelete(false);
        sObjectField64.setDefaultedOnCreate(false);
        sObjectField64.setExternalId(false);
        sObjectField64.setSoapType("xsd:double");
        sObjectField64.setGroupable(false);
        sObjectField64.setCustom(true);
        sObjectField64.setScale(3);
        sObjectField64.setCalculated(false);
        sObjectField64.setRestrictedDelete(false);
        sObjectField64.setNamePointing(false);
        sObjectField64.setIdLookup(false);
        sObjectField64.setType("double");
        sObjectField64.setSortable(true);
        sObjectField64.setLength(0);
        sObjectField64.setPrecision(6);
        sObjectField64.setByteLength(0);
        sObjectField64.setQueryByDistance(false);
        sObjectField64.setFilterable(true);
        sObjectField64.setUpdateable(true);
        sObjectField64.setUnique(false);
        sObjectField64.setAutoNumber(false);

        final SObjectField sObjectField65 = new SObjectField();
        fields1.add(sObjectField65);

        sObjectField65.setWriteRequiresMasterRead(false);
        sObjectField65.setNillable(true);
        sObjectField65.setCreateable(true);
        sObjectField65.setEncrypted(false);
        sObjectField65.setDigits("0");
        sObjectField65.setDependentPicklist(false);
        sObjectField65.setLabel("Shipping_Location (Longitude)");
        sObjectField65.setHighScaleNumber(false);
        sObjectField65.setDisplayLocationInDecimal(false);
        sObjectField65.setName("Shipping_Location__Longitude__s");
        sObjectField65.setHtmlFormatted(false);
        sObjectField65.setDeprecatedAndHidden(false);
        sObjectField65.setRestrictedPicklist(false);
        sObjectField65.setNameField(false);
        sObjectField65.setCaseSensitive(false);
        sObjectField65.setPermissionable(true);
        sObjectField65.setCascadeDelete(false);
        sObjectField65.setDefaultedOnCreate(false);
        sObjectField65.setExternalId(false);
        sObjectField65.setSoapType("xsd:double");
        sObjectField65.setGroupable(false);
        sObjectField65.setCustom(true);
        sObjectField65.setScale(3);
        sObjectField65.setCalculated(false);
        sObjectField65.setRestrictedDelete(false);
        sObjectField65.setNamePointing(false);
        sObjectField65.setIdLookup(false);
        sObjectField65.setType("double");
        sObjectField65.setSortable(true);
        sObjectField65.setLength(0);
        sObjectField65.setPrecision(6);
        sObjectField65.setByteLength(0);
        sObjectField65.setQueryByDistance(false);
        sObjectField65.setFilterable(true);
        sObjectField65.setUpdateable(true);
        sObjectField65.setUnique(false);
        sObjectField65.setAutoNumber(false);

        final SObjectField sObjectField66 = new SObjectField();
        fields1.add(sObjectField66);

        sObjectField66.setWriteRequiresMasterRead(false);
        sObjectField66.setNillable(true);
        sObjectField66.setCreateable(false);
        sObjectField66.setEncrypted(false);
        sObjectField66.setDigits("0");
        sObjectField66.setDependentPicklist(false);
        sObjectField66.setLabel("Shipping_Location");
        sObjectField66.setHighScaleNumber(false);
        sObjectField66.setDisplayLocationInDecimal(false);
        sObjectField66.setName("Shipping_Location__c");
        sObjectField66.setHtmlFormatted(false);
        sObjectField66.setDeprecatedAndHidden(false);
        sObjectField66.setRestrictedPicklist(false);
        sObjectField66.setNameField(false);
        sObjectField66.setCaseSensitive(false);
        sObjectField66.setPermissionable(true);
        sObjectField66.setCascadeDelete(false);
        sObjectField66.setDefaultedOnCreate(false);
        sObjectField66.setExternalId(false);
        sObjectField66.setSoapType("urn:location");
        sObjectField66.setGroupable(false);
        sObjectField66.setCustom(true);
        sObjectField66.setScale(0);
        sObjectField66.setCalculated(false);
        sObjectField66.setRestrictedDelete(false);
        sObjectField66.setNamePointing(false);
        sObjectField66.setIdLookup(false);
        sObjectField66.setType("location");
        sObjectField66.setSortable(false);
        sObjectField66.setLength(0);
        sObjectField66.setPrecision(0);
        sObjectField66.setByteLength(0);
        sObjectField66.setQueryByDistance(false);
        sObjectField66.setFilterable(false);
        sObjectField66.setUpdateable(false);
        sObjectField66.setUnique(false);
        sObjectField66.setAutoNumber(false);


        description.setActivateable(false);
        description.setLabelPlural("Accounts");
        description.setUpdateable(true);
        description.setDeletable(true);

        return description;
    }
}
//CHECKSTYLE:ON
