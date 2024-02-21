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
package org.apache.camel.component.salesforce;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.salesforce.api.dto.CreateSObjectResult;
import org.apache.camel.component.salesforce.dto.generated.Account;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test support for Salesforce compound data types. This test requires a custom field in the <code>Account</code> object
 * called <code>"Shipping Location"</code> of type <code>Geolocation</code> in decimal units.
 *
 * @see <a href=
 *      "https://www.salesforce.com/developer/docs/api/index_Left.htm#CSHID=compound_fields.htm|StartTopic=Content%2Fcompound_fields.htm|SkinName=webhelp">Compound
 *      data types</a>
 */
public class CompoundTypesManualIT extends AbstractSalesforceTestBase {

    private static final Logger LOG = LoggerFactory.getLogger(CompoundTypesManualIT.class);

    @Test
    public void testTypes() throws Exception {
        doTestTypes("");
        doTestTypes("Xml");
    }

    private void doTestTypes(String suffix) {

        Account account = new Account();

        account.setName("Camel Test Account");

        account.setBillingCity("San Francisco");
        account.setBillingCountry("USA");
        account.setBillingPostalCode("94105");
        account.setBillingState("CA");
        account.setBillingStreet("1 Market St #300");
        account.setBillingLatitude(37.793779);
        account.setBillingLongitude(-122.39448);

        account.setShippingCity("San Francisco");
        account.setShippingCountry("USA");
        account.setShippingPostalCode("94105");
        account.setShippingState("CA");
        account.setShippingStreet("1 Market St #300");
        account.setShippingLatitude(37.793779);
        account.setShippingLongitude(-122.39448);

        account.setShipping_Location__Latitude__s(37.793779);
        account.setShipping_Location__Longitude__s(-122.39448);

        CreateSObjectResult result
                = template().requestBody("direct:createSObject" + suffix, account, CreateSObjectResult.class);
        assertNotNull(result);
        assertTrue(result.getSuccess(), "Create success");
        LOG.debug("Create: {}", result);

        try {

            // get account with compound fields
            account = template().requestBody("direct:getSObject" + suffix, result.getId(), Account.class);
            assertNotNull(account);
            assertNotNull(account.getBillingAddress(), "Billing Address");
            assertNotNull(account.getShippingAddress(), "Shipping Address");
            assertNotNull(account.getShippingAddress(), "Shipping Location");

            LOG.debug("Retrieved fields billing address: {}, shipping location: {}", account.getBillingAddress(),
                    account.getShipping_Location__c());

        } finally {
            // delete the test SObject
            String id = (String) template().requestBody("direct:deleteSObject" + suffix, result.getId());
            assertEquals(id, result.getId());
            LOG.debug("Delete successful");
        }
    }

    @Override
    protected RouteBuilder doCreateRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // testCreateSObject
                from("direct:createSObject").to("salesforce:createSObject?sObjectName=Account");

                from("direct:createSObjectXml").to("salesforce:createSObject?format=XML&sObjectName=Account");

                // testGetSObject
                from("direct:getSObject").to(
                        "salesforce:getSObject?sObjectName=Account&sObjectFields=Id,BillingAddress,ShippingAddress,Shipping_Location__c");

                from("direct:getSObjectXml").to(
                        "salesforce:getSObject?format=XML&sObjectName=Account&sObjectFields=Id,BillingAddress,ShippingAddress,Shipping_Location__c");

                // testDeleteSObject
                from("direct:deleteSObject").to("salesforce:deleteSObject?sObjectName=Account");

                from("direct:deleteSObjectXml").to("salesforce:deleteSObject?format=XML&sObjectName=Account");
            }
        };
    }

}
