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
package org.apache.camel.component.salesforce.api.utils;

import org.apache.camel.component.salesforce.api.dto.SObjectField;
import org.apache.camel.component.salesforce.dto.generated.Account;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class QueryHelperTest {

    @Test
    public void shouldFilterAndGatherAllFieldNames() {
        assertThat(QueryHelper.filteredFieldNamesOf(new Account(), SObjectField::isCustom)).contains("External_Id__c", "Shipping_Location__c");
    }

    @Test
    public void shouldGatherAllFieldNames() {
        assertThat(QueryHelper.fieldNamesOf(new Account())).contains("Id", "Name", "ShippingCity");
    }

    @Test
    public void shouldGenerateQueryForAllFields() {
        assertThat(QueryHelper.queryToFetchAllFieldsOf(new Account()))
            .isEqualTo("SELECT Id, IsDeleted, MasterRecordId, Name, Type, ParentId, BillingStreet, BillingCity, "
                       + "BillingState, BillingPostalCode, BillingCountry, BillingLatitude, BillingLongitude, "
                       + "BillingGeocodeAccuracy, BillingAddress, ShippingStreet, ShippingCity, ShippingState, "
                       + "ShippingPostalCode, ShippingCountry, ShippingLatitude, ShippingLongitude, "
                       + "ShippingGeocodeAccuracy, ShippingAddress, Phone, Fax, AccountNumber, Website, "
                       + "PhotoUrl, Sic, Industry, AnnualRevenue, NumberOfEmployees, Ownership, TickerSymbol, "
                       + "Description, Rating, Site, OwnerId, CreatedDate, CreatedById, LastModifiedDate, "
                       + "LastModifiedById, SystemModstamp, LastActivityDate, LastViewedDate, LastReferencedDate, "
                       + "Jigsaw, JigsawCompanyId, CleanStatus, AccountSource, DunsNumber, Tradestyle, NaicsCode, "
                       + "NaicsDesc, YearStarted, SicDesc, DandbCompanyId, OperatingHoursId, Shipping_Location__Latitude__s, "
                       + "Shipping_Location__Longitude__s, Shipping_Location__c, External_Id__c FROM Account");
    }

    @Test
    public void shouldGenerateQueryForFilteredFields() {
        String s = QueryHelper.queryToFetchFilteredFieldsOf(new Account(), SObjectField::isCustom);
        assertThat(QueryHelper.queryToFetchFilteredFieldsOf(new Account(), SObjectField::isCustom))
            .isEqualTo("SELECT Shipping_Location__Latitude__s, Shipping_Location__Longitude__s, Shipping_Location__c, External_Id__c FROM Account");
    }
}
