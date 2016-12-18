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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import org.apache.camel.component.salesforce.api.dto.AbstractDescribedSObjectBase;
import org.apache.camel.component.salesforce.api.dto.SObjectDescription;

// CHECKSTYLE:OFF
/**
 * Salesforce DTO for SObject Contact
 */
@XStreamAlias("Contact")
public class Contact extends AbstractDescribedSObjectBase {

    private static final SObjectDescription DESCRIPTION = createDescription();
    // AccountId
    private String AccountId;

    // AssistantName
    private String AssistantName;

    // AssistantPhone
    private String AssistantPhone;

    // Birthdate
    private java.time.ZonedDateTime Birthdate;

    // Department
    private String Department;

    // Description
    private String Description;

    // Email
    private String Email;

    // EmailBouncedDate
    private java.time.ZonedDateTime EmailBouncedDate;

    // EmailBouncedReason
    private String EmailBouncedReason;

    // Fax
    private String Fax;

    // FirstName
    private String FirstName;

    // HomePhone
    private String HomePhone;

    // IsEmailBounced
    private Boolean IsEmailBounced;

    // Jigsaw
    private String Jigsaw;

    // JigsawContactId
    private String JigsawContactId;

    // Languages__c
    private String Languages__c;

    // LastCURequestDate
    private java.time.ZonedDateTime LastCURequestDate;

    // LastCUUpdateDate
    private java.time.ZonedDateTime LastCUUpdateDate;

    // LastName
    private String LastName;

    // MailingAddress
    private org.apache.camel.component.salesforce.api.dto.Address MailingAddress;

    // MailingCity
    private String MailingCity;

    // MailingCountry
    private String MailingCountry;

    // MailingLatitude
    private Double MailingLatitude;

    // MailingLongitude
    private Double MailingLongitude;

    // MailingPostalCode
    private String MailingPostalCode;

    // MailingState
    private String MailingState;

    // MailingStreet
    private String MailingStreet;

    // MasterRecordId
    private String MasterRecordId;

    // MobilePhone
    private String MobilePhone;

    // OtherAddress
    private org.apache.camel.component.salesforce.api.dto.Address OtherAddress;

    // OtherCity
    private String OtherCity;

    // OtherCountry
    private String OtherCountry;

    // OtherLatitude
    private Double OtherLatitude;

    // OtherLongitude
    private Double OtherLongitude;

    // OtherPhone
    private String OtherPhone;

    // OtherPostalCode
    private String OtherPostalCode;

    // OtherState
    private String OtherState;

    // OtherStreet
    private String OtherStreet;

    // Phone
    private String Phone;

    // PhotoUrl
    private String PhotoUrl;

    // ReportsToId
    private String ReportsToId;

    // Title
    private String Title;

    private static SObjectDescription createDescription() {
        final SObjectDescription description = new SObjectDescription();

        description.setName("Contact");
        description.setLabelPlural("Contacts");

        return description;
    }

    @Override
    public SObjectDescription description() {
        return DESCRIPTION;
    }

    @JsonProperty("AccountId")
    public String getAccountId() {
        return this.AccountId;
    }

    @JsonProperty("AssistantName")
    public String getAssistantName() {
        return this.AssistantName;
    }

    @JsonProperty("AssistantPhone")
    public String getAssistantPhone() {
        return this.AssistantPhone;
    }

    @JsonProperty("Birthdate")
    public java.time.ZonedDateTime getBirthdate() {
        return this.Birthdate;
    }

    @JsonProperty("Department")
    public String getDepartment() {
        return this.Department;
    }

    @JsonProperty("Description")
    public String getDescription() {
        return this.Description;
    }

    @JsonProperty("Email")
    public String getEmail() {
        return this.Email;
    }

    @JsonProperty("EmailBouncedDate")
    public java.time.ZonedDateTime getEmailBouncedDate() {
        return this.EmailBouncedDate;
    }

    @JsonProperty("EmailBouncedReason")
    public String getEmailBouncedReason() {
        return this.EmailBouncedReason;
    }

    @JsonProperty("Fax")
    public String getFax() {
        return this.Fax;
    }

    @JsonProperty("FirstName")
    public String getFirstName() {
        return this.FirstName;
    }

    @JsonProperty("HomePhone")
    public String getHomePhone() {
        return this.HomePhone;
    }

    @JsonProperty("IsEmailBounced")
    public Boolean getIsEmailBounced() {
        return this.IsEmailBounced;
    }

    @JsonProperty("Jigsaw")
    public String getJigsaw() {
        return this.Jigsaw;
    }

    @JsonProperty("JigsawContactId")
    public String getJigsawContactId() {
        return this.JigsawContactId;
    }

    @JsonProperty("Languages__c")
    public String getLanguages__c() {
        return this.Languages__c;
    }

    @JsonProperty("LastCURequestDate")
    public java.time.ZonedDateTime getLastCURequestDate() {
        return this.LastCURequestDate;
    }

    @JsonProperty("LastCUUpdateDate")
    public java.time.ZonedDateTime getLastCUUpdateDate() {
        return this.LastCUUpdateDate;
    }

    @JsonProperty("LastName")
    public String getLastName() {
        return this.LastName;
    }

    @JsonProperty("MailingAddress")
    public org.apache.camel.component.salesforce.api.dto.Address getMailingAddress() {
        return this.MailingAddress;
    }

    @JsonProperty("MailingCity")
    public String getMailingCity() {
        return this.MailingCity;
    }

    @JsonProperty("MailingCountry")
    public String getMailingCountry() {
        return this.MailingCountry;
    }

    @JsonProperty("MailingLatitude")
    public Double getMailingLatitude() {
        return this.MailingLatitude;
    }

    @JsonProperty("MailingLongitude")
    public Double getMailingLongitude() {
        return this.MailingLongitude;
    }

    @JsonProperty("MailingPostalCode")
    public String getMailingPostalCode() {
        return this.MailingPostalCode;
    }

    @JsonProperty("MailingState")
    public String getMailingState() {
        return this.MailingState;
    }

    @JsonProperty("MailingStreet")
    public String getMailingStreet() {
        return this.MailingStreet;
    }

    @JsonProperty("MasterRecordId")
    public String getMasterRecordId() {
        return this.MasterRecordId;
    }

    @JsonProperty("MobilePhone")
    public String getMobilePhone() {
        return this.MobilePhone;
    }

    @JsonProperty("OtherAddress")
    public org.apache.camel.component.salesforce.api.dto.Address getOtherAddress() {
        return this.OtherAddress;
    }

    @JsonProperty("OtherCity")
    public String getOtherCity() {
        return this.OtherCity;
    }

    @JsonProperty("OtherCountry")
    public String getOtherCountry() {
        return this.OtherCountry;
    }

    @JsonProperty("OtherLatitude")
    public Double getOtherLatitude() {
        return this.OtherLatitude;
    }

    @JsonProperty("OtherLongitude")
    public Double getOtherLongitude() {
        return this.OtherLongitude;
    }

    @JsonProperty("OtherPhone")
    public String getOtherPhone() {
        return this.OtherPhone;
    }

    @JsonProperty("OtherPostalCode")
    public String getOtherPostalCode() {
        return this.OtherPostalCode;
    }

    @JsonProperty("OtherState")
    public String getOtherState() {
        return this.OtherState;
    }

    @JsonProperty("OtherStreet")
    public String getOtherStreet() {
        return this.OtherStreet;
    }

    @JsonProperty("Phone")
    public String getPhone() {
        return this.Phone;
    }

    @JsonProperty("PhotoUrl")
    public String getPhotoUrl() {
        return this.PhotoUrl;
    }

    @JsonProperty("ReportsToId")
    public String getReportsToId() {
        return this.ReportsToId;
    }

    @JsonProperty("Title")
    public String getTitle() {
        return this.Title;
    }

    @JsonProperty("AccountId")
    public void setAccountId(final String AccountId) {
        this.AccountId = AccountId;
    }

    @JsonProperty("AssistantName")
    public void setAssistantName(final String AssistantName) {
        this.AssistantName = AssistantName;
    }

    @JsonProperty("AssistantPhone")
    public void setAssistantPhone(final String AssistantPhone) {
        this.AssistantPhone = AssistantPhone;
    }

    @JsonProperty("Birthdate")
    public void setBirthdate(final java.time.ZonedDateTime Birthdate) {
        this.Birthdate = Birthdate;
    }

    @JsonProperty("Department")
    public void setDepartment(final String Department) {
        this.Department = Department;
    }

    @JsonProperty("Description")
    public void setDescription(final String Description) {
        this.Description = Description;
    }

    @JsonProperty("Email")
    public void setEmail(final String Email) {
        this.Email = Email;
    }

    @JsonProperty("EmailBouncedDate")
    public void setEmailBouncedDate(final java.time.ZonedDateTime EmailBouncedDate) {
        this.EmailBouncedDate = EmailBouncedDate;
    }

    @JsonProperty("EmailBouncedReason")
    public void setEmailBouncedReason(final String EmailBouncedReason) {
        this.EmailBouncedReason = EmailBouncedReason;
    }

    @JsonProperty("Fax")
    public void setFax(final String Fax) {
        this.Fax = Fax;
    }

    @JsonProperty("FirstName")
    public void setFirstName(final String FirstName) {
        this.FirstName = FirstName;
    }

    @JsonProperty("HomePhone")
    public void setHomePhone(final String HomePhone) {
        this.HomePhone = HomePhone;
    }

    @JsonProperty("IsEmailBounced")
    public void setIsEmailBounced(final Boolean IsEmailBounced) {
        this.IsEmailBounced = IsEmailBounced;
    }

    @JsonProperty("Jigsaw")
    public void setJigsaw(final String Jigsaw) {
        this.Jigsaw = Jigsaw;
    }

    @JsonProperty("JigsawContactId")
    public void setJigsawContactId(final String JigsawContactId) {
        this.JigsawContactId = JigsawContactId;
    }

    @JsonProperty("Languages__c")
    public void setLanguages__c(final String Languages__c) {
        this.Languages__c = Languages__c;
    }

    @JsonProperty("LastCURequestDate")
    public void setLastCURequestDate(final java.time.ZonedDateTime LastCURequestDate) {
        this.LastCURequestDate = LastCURequestDate;
    }

    @JsonProperty("LastCUUpdateDate")
    public void setLastCUUpdateDate(final java.time.ZonedDateTime LastCUUpdateDate) {
        this.LastCUUpdateDate = LastCUUpdateDate;
    }

    @JsonProperty("LastName")
    public void setLastName(final String LastName) {
        this.LastName = LastName;
    }

    @JsonProperty("MailingAddress")
    public void setMailingAddress(final org.apache.camel.component.salesforce.api.dto.Address MailingAddress) {
        this.MailingAddress = MailingAddress;
    }

    @JsonProperty("MailingCity")
    public void setMailingCity(final String MailingCity) {
        this.MailingCity = MailingCity;
    }

    @JsonProperty("MailingCountry")
    public void setMailingCountry(final String MailingCountry) {
        this.MailingCountry = MailingCountry;
    }

    @JsonProperty("MailingLatitude")
    public void setMailingLatitude(final Double MailingLatitude) {
        this.MailingLatitude = MailingLatitude;
    }

    @JsonProperty("MailingLongitude")
    public void setMailingLongitude(final Double MailingLongitude) {
        this.MailingLongitude = MailingLongitude;
    }

    @JsonProperty("MailingPostalCode")
    public void setMailingPostalCode(final String MailingPostalCode) {
        this.MailingPostalCode = MailingPostalCode;
    }

    @JsonProperty("MailingState")
    public void setMailingState(final String MailingState) {
        this.MailingState = MailingState;
    }

    @JsonProperty("MailingStreet")
    public void setMailingStreet(final String MailingStreet) {
        this.MailingStreet = MailingStreet;
    }

    @JsonProperty("MasterRecordId")
    public void setMasterRecordId(final String MasterRecordId) {
        this.MasterRecordId = MasterRecordId;
    }

    @JsonProperty("MobilePhone")
    public void setMobilePhone(final String MobilePhone) {
        this.MobilePhone = MobilePhone;
    }

    @JsonProperty("OtherAddress")
    public void setOtherAddress(final org.apache.camel.component.salesforce.api.dto.Address OtherAddress) {
        this.OtherAddress = OtherAddress;
    }

    @JsonProperty("OtherCity")
    public void setOtherCity(final String OtherCity) {
        this.OtherCity = OtherCity;
    }

    @JsonProperty("OtherCountry")
    public void setOtherCountry(final String OtherCountry) {
        this.OtherCountry = OtherCountry;
    }

    @JsonProperty("OtherLatitude")
    public void setOtherLatitude(final Double OtherLatitude) {
        this.OtherLatitude = OtherLatitude;
    }

    @JsonProperty("OtherLongitude")
    public void setOtherLongitude(final Double OtherLongitude) {
        this.OtherLongitude = OtherLongitude;
    }

    @JsonProperty("OtherPhone")
    public void setOtherPhone(final String OtherPhone) {
        this.OtherPhone = OtherPhone;
    }

    @JsonProperty("OtherPostalCode")
    public void setOtherPostalCode(final String OtherPostalCode) {
        this.OtherPostalCode = OtherPostalCode;
    }

    @JsonProperty("OtherState")
    public void setOtherState(final String OtherState) {
        this.OtherState = OtherState;
    }

    @JsonProperty("OtherStreet")
    public void setOtherStreet(final String OtherStreet) {
        this.OtherStreet = OtherStreet;
    }

    @JsonProperty("Phone")
    public void setPhone(final String Phone) {
        this.Phone = Phone;
    }

    @JsonProperty("PhotoUrl")
    public void setPhotoUrl(final String PhotoUrl) {
        this.PhotoUrl = PhotoUrl;
    }

    @JsonProperty("ReportsToId")
    public void setReportsToId(final String ReportsToId) {
        this.ReportsToId = ReportsToId;
    }

    @JsonProperty("Title")
    public void setTitle(final String Title) {
        this.Title = Title;
    }

}
// CHECKSTYLE:ON
