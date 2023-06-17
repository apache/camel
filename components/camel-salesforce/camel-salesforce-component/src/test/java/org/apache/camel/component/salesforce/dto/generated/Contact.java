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
import org.apache.camel.component.salesforce.api.dto.AbstractDescribedSObjectBase;
import org.apache.camel.component.salesforce.api.dto.SObjectDescription;
import org.apache.camel.component.salesforce.api.dto.SObjectDescriptionUrls;
import org.apache.camel.component.salesforce.api.dto.SObjectField;

/**
 * Salesforce DTO for SObject Contact
 */
public class Contact extends AbstractDescribedSObjectBase {

    public Contact() {
        getAttributes().setType("Contact");
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

    private Contact MasterRecord;

    @JsonProperty("MasterRecord")
    public Contact getMasterRecord() {
        return this.MasterRecord;
    }

    @JsonProperty("MasterRecord")
    public void setMasterRecord(Contact MasterRecord) {
        this.MasterRecord = MasterRecord;
    }

    private String AccountId;

    @JsonProperty("AccountId")
    public String getAccountId() {
        return this.AccountId;
    }

    @JsonProperty("AccountId")
    public void setAccountId(String AccountId) {
        this.AccountId = AccountId;
    }

    private Account Account;

    @JsonProperty("Account")
    public Account getAccount() {
        return this.Account;
    }

    @JsonProperty("Account")
    public void setAccount(Account Account) {
        this.Account = Account;
    }

    private String LastName;

    @JsonProperty("LastName")
    public String getLastName() {
        return this.LastName;
    }

    @JsonProperty("LastName")
    public void setLastName(String LastName) {
        this.LastName = LastName;
    }

    private String FirstName;

    @JsonProperty("FirstName")
    public String getFirstName() {
        return this.FirstName;
    }

    @JsonProperty("FirstName")
    public void setFirstName(String FirstName) {
        this.FirstName = FirstName;
    }

    private String OtherStreet;

    @JsonProperty("OtherStreet")
    public String getOtherStreet() {
        return this.OtherStreet;
    }

    @JsonProperty("OtherStreet")
    public void setOtherStreet(String OtherStreet) {
        this.OtherStreet = OtherStreet;
    }

    private String OtherCity;

    @JsonProperty("OtherCity")
    public String getOtherCity() {
        return this.OtherCity;
    }

    @JsonProperty("OtherCity")
    public void setOtherCity(String OtherCity) {
        this.OtherCity = OtherCity;
    }

    private String OtherState;

    @JsonProperty("OtherState")
    public String getOtherState() {
        return this.OtherState;
    }

    @JsonProperty("OtherState")
    public void setOtherState(String OtherState) {
        this.OtherState = OtherState;
    }

    private String OtherPostalCode;

    @JsonProperty("OtherPostalCode")
    public String getOtherPostalCode() {
        return this.OtherPostalCode;
    }

    @JsonProperty("OtherPostalCode")
    public void setOtherPostalCode(String OtherPostalCode) {
        this.OtherPostalCode = OtherPostalCode;
    }

    private String OtherCountry;

    @JsonProperty("OtherCountry")
    public String getOtherCountry() {
        return this.OtherCountry;
    }

    @JsonProperty("OtherCountry")
    public void setOtherCountry(String OtherCountry) {
        this.OtherCountry = OtherCountry;
    }

    private Double OtherLatitude;

    @JsonProperty("OtherLatitude")
    public Double getOtherLatitude() {
        return this.OtherLatitude;
    }

    @JsonProperty("OtherLatitude")
    public void setOtherLatitude(Double OtherLatitude) {
        this.OtherLatitude = OtherLatitude;
    }

    private Double OtherLongitude;

    @JsonProperty("OtherLongitude")
    public Double getOtherLongitude() {
        return this.OtherLongitude;
    }

    @JsonProperty("OtherLongitude")
    public void setOtherLongitude(Double OtherLongitude) {
        this.OtherLongitude = OtherLongitude;
    }

    private org.apache.camel.component.salesforce.api.dto.Address OtherAddress;

    @JsonProperty("OtherAddress")
    public org.apache.camel.component.salesforce.api.dto.Address getOtherAddress() {
        return this.OtherAddress;
    }

    @JsonProperty("OtherAddress")
    public void setOtherAddress(org.apache.camel.component.salesforce.api.dto.Address OtherAddress) {
        this.OtherAddress = OtherAddress;
    }

    private String MailingStreet;

    @JsonProperty("MailingStreet")
    public String getMailingStreet() {
        return this.MailingStreet;
    }

    @JsonProperty("MailingStreet")
    public void setMailingStreet(String MailingStreet) {
        this.MailingStreet = MailingStreet;
    }

    private String MailingCity;

    @JsonProperty("MailingCity")
    public String getMailingCity() {
        return this.MailingCity;
    }

    @JsonProperty("MailingCity")
    public void setMailingCity(String MailingCity) {
        this.MailingCity = MailingCity;
    }

    private String MailingState;

    @JsonProperty("MailingState")
    public String getMailingState() {
        return this.MailingState;
    }

    @JsonProperty("MailingState")
    public void setMailingState(String MailingState) {
        this.MailingState = MailingState;
    }

    private String MailingPostalCode;

    @JsonProperty("MailingPostalCode")
    public String getMailingPostalCode() {
        return this.MailingPostalCode;
    }

    @JsonProperty("MailingPostalCode")
    public void setMailingPostalCode(String MailingPostalCode) {
        this.MailingPostalCode = MailingPostalCode;
    }

    private String MailingCountry;

    @JsonProperty("MailingCountry")
    public String getMailingCountry() {
        return this.MailingCountry;
    }

    @JsonProperty("MailingCountry")
    public void setMailingCountry(String MailingCountry) {
        this.MailingCountry = MailingCountry;
    }

    private Double MailingLatitude;

    @JsonProperty("MailingLatitude")
    public Double getMailingLatitude() {
        return this.MailingLatitude;
    }

    @JsonProperty("MailingLatitude")
    public void setMailingLatitude(Double MailingLatitude) {
        this.MailingLatitude = MailingLatitude;
    }

    private Double MailingLongitude;

    @JsonProperty("MailingLongitude")
    public Double getMailingLongitude() {
        return this.MailingLongitude;
    }

    @JsonProperty("MailingLongitude")
    public void setMailingLongitude(Double MailingLongitude) {
        this.MailingLongitude = MailingLongitude;
    }

    private org.apache.camel.component.salesforce.api.dto.Address MailingAddress;

    @JsonProperty("MailingAddress")
    public org.apache.camel.component.salesforce.api.dto.Address getMailingAddress() {
        return this.MailingAddress;
    }

    @JsonProperty("MailingAddress")
    public void setMailingAddress(org.apache.camel.component.salesforce.api.dto.Address MailingAddress) {
        this.MailingAddress = MailingAddress;
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

    private String MobilePhone;

    @JsonProperty("MobilePhone")
    public String getMobilePhone() {
        return this.MobilePhone;
    }

    @JsonProperty("MobilePhone")
    public void setMobilePhone(String MobilePhone) {
        this.MobilePhone = MobilePhone;
    }

    private String HomePhone;

    @JsonProperty("HomePhone")
    public String getHomePhone() {
        return this.HomePhone;
    }

    @JsonProperty("HomePhone")
    public void setHomePhone(String HomePhone) {
        this.HomePhone = HomePhone;
    }

    private String OtherPhone;

    @JsonProperty("OtherPhone")
    public String getOtherPhone() {
        return this.OtherPhone;
    }

    @JsonProperty("OtherPhone")
    public void setOtherPhone(String OtherPhone) {
        this.OtherPhone = OtherPhone;
    }

    private String AssistantPhone;

    @JsonProperty("AssistantPhone")
    public String getAssistantPhone() {
        return this.AssistantPhone;
    }

    @JsonProperty("AssistantPhone")
    public void setAssistantPhone(String AssistantPhone) {
        this.AssistantPhone = AssistantPhone;
    }

    private String ReportsToId;

    @JsonProperty("ReportsToId")
    public String getReportsToId() {
        return this.ReportsToId;
    }

    @JsonProperty("ReportsToId")
    public void setReportsToId(String ReportsToId) {
        this.ReportsToId = ReportsToId;
    }

    private Contact ReportsTo;

    @JsonProperty("ReportsTo")
    public Contact getReportsTo() {
        return this.ReportsTo;
    }

    @JsonProperty("ReportsTo")
    public void setReportsTo(Contact ReportsTo) {
        this.ReportsTo = ReportsTo;
    }

    private String Email;

    @JsonProperty("Email")
    public String getEmail() {
        return this.Email;
    }

    @JsonProperty("Email")
    public void setEmail(String Email) {
        this.Email = Email;
    }

    private String Title;

    @JsonProperty("Title")
    public String getTitle() {
        return this.Title;
    }

    @JsonProperty("Title")
    public void setTitle(String Title) {
        this.Title = Title;
    }

    private String Department;

    @JsonProperty("Department")
    public String getDepartment() {
        return this.Department;
    }

    @JsonProperty("Department")
    public void setDepartment(String Department) {
        this.Department = Department;
    }

    private String AssistantName;

    @JsonProperty("AssistantName")
    public String getAssistantName() {
        return this.AssistantName;
    }

    @JsonProperty("AssistantName")
    public void setAssistantName(String AssistantName) {
        this.AssistantName = AssistantName;
    }

    private java.time.LocalDate Birthdate;

    @JsonProperty("Birthdate")
    public java.time.LocalDate getBirthdate() {
        return this.Birthdate;
    }

    @JsonProperty("Birthdate")
    public void setBirthdate(java.time.LocalDate Birthdate) {
        this.Birthdate = Birthdate;
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

    private java.time.ZonedDateTime LastCURequestDate;

    @JsonProperty("LastCURequestDate")
    public java.time.ZonedDateTime getLastCURequestDate() {
        return this.LastCURequestDate;
    }

    @JsonProperty("LastCURequestDate")
    public void setLastCURequestDate(java.time.ZonedDateTime LastCURequestDate) {
        this.LastCURequestDate = LastCURequestDate;
    }

    private java.time.ZonedDateTime LastCUUpdateDate;

    @JsonProperty("LastCUUpdateDate")
    public java.time.ZonedDateTime getLastCUUpdateDate() {
        return this.LastCUUpdateDate;
    }

    @JsonProperty("LastCUUpdateDate")
    public void setLastCUUpdateDate(java.time.ZonedDateTime LastCUUpdateDate) {
        this.LastCUUpdateDate = LastCUUpdateDate;
    }

    private String EmailBouncedReason;

    @JsonProperty("EmailBouncedReason")
    public String getEmailBouncedReason() {
        return this.EmailBouncedReason;
    }

    @JsonProperty("EmailBouncedReason")
    public void setEmailBouncedReason(String EmailBouncedReason) {
        this.EmailBouncedReason = EmailBouncedReason;
    }

    private java.time.ZonedDateTime EmailBouncedDate;

    @JsonProperty("EmailBouncedDate")
    public java.time.ZonedDateTime getEmailBouncedDate() {
        return this.EmailBouncedDate;
    }

    @JsonProperty("EmailBouncedDate")
    public void setEmailBouncedDate(java.time.ZonedDateTime EmailBouncedDate) {
        this.EmailBouncedDate = EmailBouncedDate;
    }

    private Boolean IsEmailBounced;

    @JsonProperty("IsEmailBounced")
    public Boolean getIsEmailBounced() {
        return this.IsEmailBounced;
    }

    @JsonProperty("IsEmailBounced")
    public void setIsEmailBounced(Boolean IsEmailBounced) {
        this.IsEmailBounced = IsEmailBounced;
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

    private String Jigsaw;

    @JsonProperty("Jigsaw")
    public String getJigsaw() {
        return this.Jigsaw;
    }

    @JsonProperty("Jigsaw")
    public void setJigsaw(String Jigsaw) {
        this.Jigsaw = Jigsaw;
    }

    private String JigsawContactId;

    @JsonProperty("JigsawContactId")
    public String getJigsawContactId() {
        return this.JigsawContactId;
    }

    @JsonProperty("JigsawContactId")
    public void setJigsawContactId(String JigsawContactId) {
        this.JigsawContactId = JigsawContactId;
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

    @Override
    public final SObjectDescription description() {
        return DESCRIPTION;
    }

    private static SObjectDescription createSObjectDescription() {
        final SObjectDescription description = new SObjectDescription();

        final List<SObjectField> fields1 = new ArrayList<>();
        description.setFields(fields1);

        final SObjectField sObjectField1 = createField("Id", "Contact ID", "id", "tns:ID", 18, false, false, false, false, false, false, true);
        fields1.add(sObjectField1);
        final SObjectField sObjectField2 = createField("IsDeleted", "Deleted", "boolean", "xsd:boolean", 0, false, false, false, false, false, false, false);
        fields1.add(sObjectField2);
        final SObjectField sObjectField3 = createField("MasterRecordId", "Master Record ID", "reference", "tns:ID", 18, false, true, false, false, false, false, false);
        fields1.add(sObjectField3);
        final SObjectField sObjectField4 = createField("AccountId", "Account ID", "reference", "tns:ID", 18, false, true, false, false, false, false, false);
        fields1.add(sObjectField4);
        final SObjectField sObjectField5 = createField("LastName", "Last Name", "string", "xsd:string", 80, false, false, false, false, false, false, false);
        fields1.add(sObjectField5);
        final SObjectField sObjectField6 = createField("FirstName", "First Name", "string", "xsd:string", 40, false, true, false, false, false, false, false);
        fields1.add(sObjectField6);
        final SObjectField sObjectField7 = createField("Salutation", "Salutation", "picklist", "xsd:string", 40, false, true, false, false, false, false, false);
        fields1.add(sObjectField7);
        final SObjectField sObjectField8 = createField("Name", "Full Name", "string", "xsd:string", 121, false, false, true, false, false, false, false);
        fields1.add(sObjectField8);
        final SObjectField sObjectField9 = createField("OtherStreet", "Other Street", "textarea", "xsd:string", 255, false, true, false, false, false, false, false);
        fields1.add(sObjectField9);
        final SObjectField sObjectField10 = createField("OtherCity", "Other City", "string", "xsd:string", 40, false, true, false, false, false, false, false);
        fields1.add(sObjectField10);
        final SObjectField sObjectField11 = createField("OtherState", "Other State/Province", "string", "xsd:string", 80, false, true, false, false, false, false, false);
        fields1.add(sObjectField11);
        final SObjectField sObjectField12 = createField("OtherPostalCode", "Other Zip/Postal Code", "string", "xsd:string", 20, false, true, false, false, false, false, false);
        fields1.add(sObjectField12);
        final SObjectField sObjectField13 = createField("OtherCountry", "Other Country", "string", "xsd:string", 80, false, true, false, false, false, false, false);
        fields1.add(sObjectField13);
        final SObjectField sObjectField14 = createField("OtherLatitude", "Other Latitude", "double", "xsd:double", 0, false, true, false, false, false, false, false);
        fields1.add(sObjectField14);
        final SObjectField sObjectField15 = createField("OtherLongitude", "Other Longitude", "double", "xsd:double", 0, false, true, false, false, false, false, false);
        fields1.add(sObjectField15);
        final SObjectField sObjectField16 = createField("OtherGeocodeAccuracy", "Other Geocode Accuracy", "picklist", "xsd:string", 40, false, true, false, false, false, false,
                                                        false);
        fields1.add(sObjectField16);
        final SObjectField sObjectField17 = createField("OtherAddress", "Other Address", "address", "urn:address", 0, false, true, false, false, false, false, false);
        fields1.add(sObjectField17);
        final SObjectField sObjectField18 = createField("MailingStreet", "Mailing Street", "textarea", "xsd:string", 255, false, true, false, false, false, false, false);
        fields1.add(sObjectField18);
        final SObjectField sObjectField19 = createField("MailingCity", "Mailing City", "string", "xsd:string", 40, false, true, false, false, false, false, false);
        fields1.add(sObjectField19);
        final SObjectField sObjectField20 = createField("MailingState", "Mailing State/Province", "string", "xsd:string", 80, false, true, false, false, false, false, false);
        fields1.add(sObjectField20);
        final SObjectField sObjectField21 = createField("MailingPostalCode", "Mailing Zip/Postal Code", "string", "xsd:string", 20, false, true, false, false, false, false, false);
        fields1.add(sObjectField21);
        final SObjectField sObjectField22 = createField("MailingCountry", "Mailing Country", "string", "xsd:string", 80, false, true, false, false, false, false, false);
        fields1.add(sObjectField22);
        final SObjectField sObjectField23 = createField("MailingLatitude", "Mailing Latitude", "double", "xsd:double", 0, false, true, false, false, false, false, false);
        fields1.add(sObjectField23);
        final SObjectField sObjectField24 = createField("MailingLongitude", "Mailing Longitude", "double", "xsd:double", 0, false, true, false, false, false, false, false);
        fields1.add(sObjectField24);
        final SObjectField sObjectField25 = createField("MailingGeocodeAccuracy", "Mailing Geocode Accuracy", "picklist", "xsd:string", 40, false, true, false, false, false, false,
                                                        false);
        fields1.add(sObjectField25);
        final SObjectField sObjectField26 = createField("MailingAddress", "Mailing Address", "address", "urn:address", 0, false, true, false, false, false, false, false);
        fields1.add(sObjectField26);
        final SObjectField sObjectField27 = createField("Phone", "Business Phone", "phone", "xsd:string", 40, false, true, false, false, false, false, false);
        fields1.add(sObjectField27);
        final SObjectField sObjectField28 = createField("Fax", "Business Fax", "phone", "xsd:string", 40, false, true, false, false, false, false, false);
        fields1.add(sObjectField28);
        final SObjectField sObjectField29 = createField("MobilePhone", "Mobile Phone", "phone", "xsd:string", 40, false, true, false, false, false, false, false);
        fields1.add(sObjectField29);
        final SObjectField sObjectField30 = createField("HomePhone", "Home Phone", "phone", "xsd:string", 40, false, true, false, false, false, false, false);
        fields1.add(sObjectField30);
        final SObjectField sObjectField31 = createField("OtherPhone", "Other Phone", "phone", "xsd:string", 40, false, true, false, false, false, false, false);
        fields1.add(sObjectField31);
        final SObjectField sObjectField32 = createField("AssistantPhone", "Asst. Phone", "phone", "xsd:string", 40, false, true, false, false, false, false, false);
        fields1.add(sObjectField32);
        final SObjectField sObjectField33 = createField("ReportsToId", "Reports To ID", "reference", "tns:ID", 18, false, true, false, false, false, false, false);
        fields1.add(sObjectField33);
        final SObjectField sObjectField34 = createField("Email", "Email", "email", "xsd:string", 80, false, true, false, false, false, false, true);
        fields1.add(sObjectField34);
        final SObjectField sObjectField35 = createField("Title", "Title", "string", "xsd:string", 128, false, true, false, false, false, false, false);
        fields1.add(sObjectField35);
        final SObjectField sObjectField36 = createField("Department", "Department", "string", "xsd:string", 80, false, true, false, false, false, false, false);
        fields1.add(sObjectField36);
        final SObjectField sObjectField37 = createField("AssistantName", "Assistant's Name", "string", "xsd:string", 40, false, true, false, false, false, false, false);
        fields1.add(sObjectField37);
        final SObjectField sObjectField38 = createField("LeadSource", "Lead Source", "picklist", "xsd:string", 40, false, true, false, false, false, false, false);
        fields1.add(sObjectField38);
        final SObjectField sObjectField39 = createField("Birthdate", "Birthdate", "date", "xsd:date", 0, false, true, false, false, false, false, false);
        fields1.add(sObjectField39);
        final SObjectField sObjectField40 = createField("Description", "Contact Description", "textarea", "xsd:string", 32000, false, true, false, false, false, false, false);
        fields1.add(sObjectField40);
        final SObjectField sObjectField41 = createField("OwnerId", "Owner ID", "reference", "tns:ID", 18, false, false, false, false, false, false, false);
        fields1.add(sObjectField41);
        final SObjectField sObjectField42 = createField("CreatedDate", "Created Date", "datetime", "xsd:dateTime", 0, false, false, false, false, false, false, false);
        fields1.add(sObjectField42);
        final SObjectField sObjectField43 = createField("CreatedById", "Created By ID", "reference", "tns:ID", 18, false, false, false, false, false, false, false);
        fields1.add(sObjectField43);
        final SObjectField sObjectField44 = createField("LastModifiedDate", "Last Modified Date", "datetime", "xsd:dateTime", 0, false, false, false, false, false, false, false);
        fields1.add(sObjectField44);
        final SObjectField sObjectField45 = createField("LastModifiedById", "Last Modified By ID", "reference", "tns:ID", 18, false, false, false, false, false, false, false);
        fields1.add(sObjectField45);
        final SObjectField sObjectField46 = createField("SystemModstamp", "System Modstamp", "datetime", "xsd:dateTime", 0, false, false, false, false, false, false, false);
        fields1.add(sObjectField46);
        final SObjectField sObjectField47 = createField("LastActivityDate", "Last Activity", "date", "xsd:date", 0, false, true, false, false, false, false, false);
        fields1.add(sObjectField47);
        final SObjectField sObjectField48 = createField("LastCURequestDate", "Last Stay-in-Touch Request Date", "datetime", "xsd:dateTime", 0, false, true, false, false, false,
                                                        false, false);
        fields1.add(sObjectField48);
        final SObjectField sObjectField49 = createField("LastCUUpdateDate", "Last Stay-in-Touch Save Date", "datetime", "xsd:dateTime", 0, false, true, false, false, false, false,
                                                        false);
        fields1.add(sObjectField49);
        final SObjectField sObjectField50 = createField("LastViewedDate", "Last Viewed Date", "datetime", "xsd:dateTime", 0, false, true, false, false, false, false, false);
        fields1.add(sObjectField50);
        final SObjectField sObjectField51 = createField("LastReferencedDate", "Last Referenced Date", "datetime", "xsd:dateTime", 0, false, true, false, false, false, false,
                                                        false);
        fields1.add(sObjectField51);
        final SObjectField sObjectField52 = createField("EmailBouncedReason", "Email Bounced Reason", "string", "xsd:string", 255, false, true, false, false, false, false, false);
        fields1.add(sObjectField52);
        final SObjectField sObjectField53 = createField("EmailBouncedDate", "Email Bounced Date", "datetime", "xsd:dateTime", 0, false, true, false, false, false, false, false);
        fields1.add(sObjectField53);
        final SObjectField sObjectField54 = createField("IsEmailBounced", "Is Email Bounced", "boolean", "xsd:boolean", 0, false, false, false, false, false, false, false);
        fields1.add(sObjectField54);
        final SObjectField sObjectField55 = createField("PhotoUrl", "Photo URL", "url", "xsd:string", 255, false, true, false, false, false, false, false);
        fields1.add(sObjectField55);
        final SObjectField sObjectField56 = createField("Jigsaw", "Data.com Key", "string", "xsd:string", 20, false, true, false, false, false, false, false);
        fields1.add(sObjectField56);
        final SObjectField sObjectField57 = createField("JigsawContactId", "Jigsaw Contact ID", "string", "xsd:string", 20, false, true, false, false, false, false, false);
        fields1.add(sObjectField57);
        final SObjectField sObjectField58 = createField("CleanStatus", "Clean Status", "picklist", "xsd:string", 40, false, true, false, false, false, false, false);
        fields1.add(sObjectField58);
        final SObjectField sObjectField59 = createField("External_Id__c", "External Id", "string", "xsd:string", 255, true, true, false, true, true, false, true);
        fields1.add(sObjectField59);

        description.setLabel("Contact");
        description.setLabelPlural("Contacts");
        description.setName("Contact");

        final SObjectDescriptionUrls sObjectDescriptionUrls1 = new SObjectDescriptionUrls();
        sObjectDescriptionUrls1.setApprovalLayouts("/services/data/v45.0/sobjects/Contact/describe/approvalLayouts");
        sObjectDescriptionUrls1.setCompactLayouts("/services/data/v45.0/sobjects/Contact/describe/compactLayouts");
        sObjectDescriptionUrls1.setDefaultValues("/services/data/v45.0/sobjects/Contact/defaultValues?recordTypeId&fields");
        sObjectDescriptionUrls1.setDescribe("/services/data/v45.0/sobjects/Contact/describe");
        sObjectDescriptionUrls1.setLayouts("/services/data/v45.0/sobjects/Contact/describe/layouts");
        sObjectDescriptionUrls1.setListviews("/services/data/v45.0/sobjects/Contact/listviews");
        sObjectDescriptionUrls1.setQuickActions("/services/data/v45.0/sobjects/Contact/quickActions");
        sObjectDescriptionUrls1.setRowTemplate("/services/data/v45.0/sobjects/Contact/{ID}");
        sObjectDescriptionUrls1.setSobject("/services/data/v45.0/sobjects/Contact");
        sObjectDescriptionUrls1.setUiDetailTemplate("https://customer-flow-8168-dev-ed.cs42.my.salesforce.com/{ID}");
        sObjectDescriptionUrls1.setUiEditTemplate("https://customer-flow-8168-dev-ed.cs42.my.salesforce.com/{ID}/e");
        sObjectDescriptionUrls1.setUiNewRecord("https://customer-flow-8168-dev-ed.cs42.my.salesforce.com/003/e");
        description.setUrls(sObjectDescriptionUrls1);

        return description;
    }
}

