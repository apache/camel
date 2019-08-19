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

import com.fasterxml.jackson.annotation.JsonProperty;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import org.apache.camel.component.salesforce.api.dto.AbstractSObjectBase;

//CHECKSTYLE:OFF
/**
 * Salesforce DTO for SObject Asset
 */
@XStreamAlias("Asset")
public class Asset extends AbstractSObjectBase {

    public Asset() {
        getAttributes().setType("Asset");
    }

    // ContactId
    private String ContactId;

    @JsonProperty("ContactId")
    public String getContactId() {
        return this.ContactId;
    }

    @JsonProperty("ContactId")
    public void setContactId(String ContactId) {
        this.ContactId = ContactId;
    }

    // AccountId
    private String AccountId;

    @JsonProperty("AccountId")
    public String getAccountId() {
        return this.AccountId;
    }

    @JsonProperty("AccountId")
    public void setAccountId(String AccountId) {
        this.AccountId = AccountId;
    }

    // Product2Id
    private String Product2Id;

    @JsonProperty("Product2Id")
    public String getProduct2Id() {
        return this.Product2Id;
    }

    @JsonProperty("Product2Id")
    public void setProduct2Id(String Product2Id) {
        this.Product2Id = Product2Id;
    }

    // IsCompetitorProduct
    private Boolean IsCompetitorProduct;

    @JsonProperty("IsCompetitorProduct")
    public Boolean getIsCompetitorProduct() {
        return this.IsCompetitorProduct;
    }

    @JsonProperty("IsCompetitorProduct")
    public void setIsCompetitorProduct(Boolean IsCompetitorProduct) {
        this.IsCompetitorProduct = IsCompetitorProduct;
    }

    // SerialNumber
    private String SerialNumber;

    @JsonProperty("SerialNumber")
    public String getSerialNumber() {
        return this.SerialNumber;
    }

    @JsonProperty("SerialNumber")
    public void setSerialNumber(String SerialNumber) {
        this.SerialNumber = SerialNumber;
    }

    // InstallDate
    private java.time.ZonedDateTime InstallDate;

    @JsonProperty("InstallDate")
    public java.time.ZonedDateTime getInstallDate() {
        return this.InstallDate;
    }

    @JsonProperty("InstallDate")
    public void setInstallDate(java.time.ZonedDateTime InstallDate) {
        this.InstallDate = InstallDate;
    }

    // PurchaseDate
    private java.time.ZonedDateTime PurchaseDate;

    @JsonProperty("PurchaseDate")
    public java.time.ZonedDateTime getPurchaseDate() {
        return this.PurchaseDate;
    }

    @JsonProperty("PurchaseDate")
    public void setPurchaseDate(java.time.ZonedDateTime PurchaseDate) {
        this.PurchaseDate = PurchaseDate;
    }

    // UsageEndDate
    private java.time.ZonedDateTime UsageEndDate;

    @JsonProperty("UsageEndDate")
    public java.time.ZonedDateTime getUsageEndDate() {
        return this.UsageEndDate;
    }

    @JsonProperty("UsageEndDate")
    public void setUsageEndDate(java.time.ZonedDateTime UsageEndDate) {
        this.UsageEndDate = UsageEndDate;
    }

    // Price
    private Double Price;

    @JsonProperty("Price")
    public Double getPrice() {
        return this.Price;
    }

    @JsonProperty("Price")
    public void setPrice(Double Price) {
        this.Price = Price;
    }

    // Quantity
    private Double Quantity;

    @JsonProperty("Quantity")
    public Double getQuantity() {
        return this.Quantity;
    }

    @JsonProperty("Quantity")
    public void setQuantity(Double Quantity) {
        this.Quantity = Quantity;
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

}
//CHECKSTYLE:ON
