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

import org.apache.camel.component.salesforce.api.dto.AbstractSObjectBase;
//CHECKSTYLE:OFF
@XStreamAlias("Line_Item__c")
public class Line_Item__c extends AbstractSObjectBase {

    private Double Unit_Price__c;

    private Double Units_Sold__c;

    private String Merchandise__c;

    private String Invoice_Statement__c;

    @JsonProperty("Unit_Price__c")
    public Double getUnit_Price__c() {
        return Unit_Price__c;
    }

    @JsonProperty("Unit_Price__c")
    public void setUnit_Price__c(Double unit_Price__c) {
        Unit_Price__c = unit_Price__c;
    }

    @JsonProperty("Units_Sold__c")
    public Double getUnits_Sold__c() {
        return Units_Sold__c;
    }

    @JsonProperty("Units_Sold__c")
    public void setUnits_Sold__c(Double units_Sold__c) {
        Units_Sold__c = units_Sold__c;
    }

    @JsonProperty("Merchandise__c")
    public String getMerchandise__c() {
        return Merchandise__c;
    }

    @JsonProperty("Merchandise__c")
    public void setMerchandise__c(String merchandise__c) {
        Merchandise__c = merchandise__c;
    }

    @JsonProperty("Invoice_Statement__c")
    public String getInvoice_Statement__c() {
        return Invoice_Statement__c;
    }

    @JsonProperty("Invoice_Statement__c")
    public void setInvoice_Statement__c(String invoice_Statement__c) {
        Invoice_Statement__c = invoice_Statement__c;
    }

}
//CHECKSTYLE:ON
