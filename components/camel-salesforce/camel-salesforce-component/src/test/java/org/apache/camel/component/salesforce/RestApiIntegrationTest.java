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
package org.apache.camel.component.salesforce;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.channels.ReadableByteChannel;
import java.util.HashMap;
import java.util.List;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.salesforce.api.dto.CreateSObjectResult;
import org.apache.camel.component.salesforce.api.dto.GlobalObjects;
import org.apache.camel.component.salesforce.api.dto.RestResources;
import org.apache.camel.component.salesforce.api.dto.SObjectBasicInfo;
import org.apache.camel.component.salesforce.api.dto.SObjectDescription;
import org.apache.camel.component.salesforce.api.dto.SearchResult;
import org.apache.camel.component.salesforce.api.dto.SearchResults;
import org.apache.camel.component.salesforce.api.dto.Version;
import org.apache.camel.component.salesforce.api.dto.Versions;
import org.apache.camel.component.salesforce.dto.generated.Document;
import org.apache.camel.component.salesforce.dto.generated.Line_Item__c;
import org.apache.camel.component.salesforce.dto.generated.Merchandise__c;
import org.apache.camel.component.salesforce.dto.generated.QueryRecordsLine_Item__c;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RestApiIntegrationTest extends AbstractSalesforceTestBase {

    private static final Logger LOG = LoggerFactory.getLogger(RestApiIntegrationTest.class);
    private static final String TEST_LINE_ITEM_ID = "1";
    private static final String NEW_LINE_ITEM_ID = "100";
    private static final String TEST_DOCUMENT_ID = "Test Document";

    private static String testId;

    @Test
    public void testGetVersions() throws Exception {
        doTestGetVersions("");
        doTestGetVersions("Xml");
    }

    @SuppressWarnings("unchecked")
    private void doTestGetVersions(String suffix) throws Exception {
        // test getVersions doesn't need a body
        // assert expected result
        Object o = template().requestBody("direct:getVersions" + suffix, (Object) null);
        List<Version> versions = null;
        if (o instanceof Versions) {
            versions = ((Versions) o).getVersions();
        } else {
            versions = (List<Version>) o;
        }
        assertNotNull(versions);
        LOG.debug("Versions: {}", versions);
    }

    @Test
    public void testGetResources() throws Exception {
        doTestGetResources("");
        doTestGetResources("Xml");
    }

    private void doTestGetResources(String suffix) throws Exception {


        RestResources resources = template().requestBody("direct:getResources" + suffix, null, RestResources.class);
        assertNotNull(resources);
        LOG.debug("Resources: {}", resources);
    }

    @Test
    public void testGetGlobalObjects() throws Exception {
        doTestGetGlobalObjects("");
        doTestGetGlobalObjects("Xml");
    }

    private void doTestGetGlobalObjects(String suffix) throws Exception {


        GlobalObjects globalObjects = template().requestBody("direct:getGlobalObjects" + suffix, null, GlobalObjects.class);
        assertNotNull(globalObjects);
        LOG.debug("GlobalObjects: {}", globalObjects);
    }

    @Test
    public void testGetBasicInfo() throws Exception {
        doTestGetBasicInfo("");
        doTestGetBasicInfo("Xml");
    }

    private void doTestGetBasicInfo(String suffix) throws Exception {
        SObjectBasicInfo objectBasicInfo = template().requestBody("direct:getBasicInfo" + suffix, null, SObjectBasicInfo.class);
        assertNotNull(objectBasicInfo);
        LOG.debug("SObjectBasicInfo: {}", objectBasicInfo);

        // set test Id for testGetSObject
        assertFalse("RecentItems is empty", objectBasicInfo.getRecentItems().isEmpty());
        testId = objectBasicInfo.getRecentItems().get(0).getId();
    }

    @Test
    public void testGetDescription() throws Exception {
        doTestGetDescription("");
        doTestGetDescription("Xml");
    }

    private void doTestGetDescription(String suffix) throws Exception {


        SObjectDescription sObjectDescription = template().requestBody("direct:getDescription" + suffix, null, SObjectDescription.class);
        assertNotNull(sObjectDescription);
        LOG.debug("SObjectDescription: {}", sObjectDescription);
    }

    @Test
    public void testGetSObject() throws Exception {
        doTestGetSObject("");
        doTestGetSObject("Xml");
    }

    private void doTestGetSObject(String suffix) throws Exception {
        if (testId == null) {
            // execute getBasicInfo to get test id from recent items
            doTestGetBasicInfo("");
        }

        Merchandise__c merchandise = template().requestBody("direct:getSObject" + suffix, testId, Merchandise__c.class);
        assertNotNull(merchandise);
        if (suffix.isEmpty()) {
            assertNull(merchandise.getTotal_Inventory__c());
            assertNotNull(merchandise.getPrice__c());
        } else {
            assertNotNull(merchandise.getTotal_Inventory__c());
            assertNull(merchandise.getPrice__c());
        }
        LOG.debug("SObjectById: {}", merchandise);
    }

    @Test
    public void testCreateUpdateDelete() throws Exception {
        doTestCreateUpdateDelete("");
        doTestCreateUpdateDelete("Xml");
    }

    private void doTestCreateUpdateDelete(String suffix) throws InterruptedException {
        Merchandise__c merchandise = new Merchandise__c();
        merchandise.setName("Wee Wee Wee Plane");
        merchandise.setDescription__c("Microlite plane");
        merchandise.setPrice__c(2000.0);
        merchandise.setTotal_Inventory__c(50.0);
        CreateSObjectResult result = template().requestBody("direct:CreateSObject" + suffix,
            merchandise, CreateSObjectResult.class);
        assertNotNull(result);
        assertTrue("Create success", result.getSuccess());
        LOG.debug("Create: " + result);

        // test JSON update
        // make the plane cheaper
        merchandise.setPrice__c(1500.0);
        // change inventory to half
        merchandise.setTotal_Inventory__c(25.0);
        // also need to set the Id
        merchandise.setId(result.getId());

        assertNull(template().requestBodyAndHeader("direct:UpdateSObject" + suffix,
            merchandise, SalesforceEndpointConfig.SOBJECT_ID, result.getId()));
        LOG.debug("Update successful");

        // delete the newly created SObject
        assertNull(template().requestBody("direct:deleteSObject" + suffix, result.getId()));
        LOG.debug("Delete successful");
    }

    @Test
    public void testCreateUpdateDeleteWithId() throws Exception {
        doTestCreateUpdateDeleteWithId("");
        doTestCreateUpdateDeleteWithId("Xml");
    }

    private void doTestCreateUpdateDeleteWithId(String suffix) throws InterruptedException {
        // get line item with Name 1
        Line_Item__c lineItem = template().requestBody("direct:getSObjectWithId" + suffix, TEST_LINE_ITEM_ID,
            Line_Item__c.class);
        assertNotNull(lineItem);
        LOG.debug("GetWithId: {}", lineItem);

        // test insert with id
        // set the unit price and sold
        lineItem.setUnit_Price__c(1000.0);
        lineItem.setUnits_Sold__c(50.0);
        // update line item with Name NEW_LINE_ITEM_ID
        lineItem.setName(NEW_LINE_ITEM_ID);

        CreateSObjectResult result = template().requestBodyAndHeader("direct:upsertSObject" + suffix,
            lineItem, SalesforceEndpointConfig.SOBJECT_EXT_ID_VALUE, NEW_LINE_ITEM_ID,
            CreateSObjectResult.class);
        assertNotNull(result);
        assertTrue(result.getSuccess());
        LOG.debug("CreateWithId: {}", result);

        // clear read only parent type fields
        lineItem.setInvoice_Statement__c(null);
        lineItem.setMerchandise__c(null);
        // change the units sold
        lineItem.setUnits_Sold__c(25.0);

        // update line item with Name NEW_LINE_ITEM_ID
        result = template().requestBodyAndHeader("direct:upsertSObject" + suffix,
            lineItem, SalesforceEndpointConfig.SOBJECT_EXT_ID_VALUE, NEW_LINE_ITEM_ID,
            CreateSObjectResult.class);
        assertNull(result);
        LOG.debug("UpdateWithId: {}", result);

        // delete the SObject with Name NEW_LINE_ITEM_ID
        assertNull(template().requestBody("direct:deleteSObjectWithId" + suffix, NEW_LINE_ITEM_ID));
        LOG.debug("DeleteWithId successful");
    }

    @Test
    public void testGetBlobField() throws Exception {
        doTestGetBlobField("");
        doTestGetBlobField("Xml");
    }

    public void doTestGetBlobField(String suffix) throws Exception {
        // get document with Name "Test Document"
        final HashMap<String, Object> headers = new HashMap<String, Object>();
        headers.put(SalesforceEndpointConfig.SOBJECT_NAME, "Document");
        headers.put(SalesforceEndpointConfig.SOBJECT_EXT_ID_NAME, "Name");
        Document document = template().requestBodyAndHeaders("direct:getSObjectWithId" + suffix, TEST_DOCUMENT_ID,
            headers, Document.class);
        assertNotNull(document);
        LOG.debug("GetWithId: {}", document);

        // get Body field for this document
        InputStream body = template().requestBody("direct:getBlobField" + suffix, document, InputStream.class);
        assertNotNull(body);
        LOG.debug("GetBlobField: {}", body);
        // write body to test file
        final FileChannel fileChannel = new FileOutputStream("target/getBlobField" + suffix + ".txt").getChannel();
        final ReadableByteChannel src = Channels.newChannel(body);
        fileChannel.transferFrom(src, 0, document.getBodyLength());
        fileChannel.close();
        src.close();
    }

    @Test
    public void testQuery() throws Exception {
        doTestQuery("");
        doTestQuery("Xml");
    }

    private void doTestQuery(String suffix) throws InterruptedException {
        QueryRecordsLine_Item__c queryRecords = template().requestBody("direct:query" + suffix, null,
            QueryRecordsLine_Item__c.class);
        assertNotNull(queryRecords);
        LOG.debug("ExecuteQuery: {}", queryRecords);
    }


    @Test
    public void testSearch() throws Exception {
        doTestSearch("");
        doTestSearch("Xml");
    }

    @SuppressWarnings("unchecked")
    private void doTestSearch(String suffix) throws InterruptedException {

        Object obj = template().requestBody("direct:search" + suffix, (Object) null);
        List<SearchResult> searchResults = null;
        if (obj instanceof SearchResults) {
            SearchResults results = (SearchResults) obj;
            searchResults = results.getResults();
        } else {
            searchResults = (List<SearchResult>) obj;
        }
        assertNotNull(searchResults);
        LOG.debug("ExecuteSearch: {}", searchResults);
    }

    @Override
    protected RouteBuilder doCreateRouteBuilder() throws Exception {

        // create test route
        return new RouteBuilder() {
            public void configure() {

                // testGetVersion
                from("direct:getVersions")
                    .to("salesforce:getVersions");

                // allow overriding format per endpoint
                from("direct:getVersionsXml")
                    .to("salesforce:getVersions?format=XML");

                // testGetResources
                from("direct:getResources")
                    .to("salesforce:getResources");

                from("direct:getResourcesXml")
                    .to("salesforce:getResources?format=XML");

                // testGetGlobalObjects
                from("direct:getGlobalObjects")
                    .to("salesforce:getGlobalObjects");

                from("direct:getGlobalObjectsXml")
                    .to("salesforce:getGlobalObjects?format=XML");

                // testGetBasicInfo
                from("direct:getBasicInfo")
                    .to("salesforce:getBasicInfo?sObjectName=Merchandise__c");

                from("direct:getBasicInfoXml")
                    .to("salesforce:getBasicInfo?format=XML&sObjectName=Merchandise__c");

                // testGetDescription
                from("direct:getDescription")
                    .to("salesforce:getDescription?sObjectName=Merchandise__c");

                from("direct:getDescriptionXml")
                    .to("salesforce:getDescription?format=XML&sObjectName=Merchandise__c");

                // testGetSObject
                from("direct:getSObject")
                    .to("salesforce:getSObject?sObjectName=Merchandise__c&sObjectFields=Description__c,Price__c");

                from("direct:getSObjectXml")
                    .to("salesforce:getSObject?format=XML&sObjectName=Merchandise__c&sObjectFields=Description__c,Total_Inventory__c");

                // testCreateSObject
                from("direct:CreateSObject")
                    .to("salesforce:createSObject?sObjectName=Merchandise__c");

                from("direct:CreateSObjectXml")
                    .to("salesforce:createSObject?format=XML&sObjectName=Merchandise__c");

                // testUpdateSObject
                from("direct:UpdateSObject")
                    .to("salesforce:updateSObject?sObjectName=Merchandise__c");

                from("direct:UpdateSObjectXml")
                    .to("salesforce:updateSObject?format=XML&sObjectName=Merchandise__c");

                // testDeleteSObject
                from("direct:deleteSObject")
                    .to("salesforce:deleteSObject?sObjectName=Merchandise__c");

                from("direct:deleteSObjectXml")
                    .to("salesforce:deleteSObject?format=XML&sObjectName=Merchandise__c");

                // testGetSObjectWithId
                from("direct:getSObjectWithId")
                    .to("salesforce:getSObjectWithId?sObjectName=Line_Item__c&sObjectIdName=Name");

                from("direct:getSObjectWithIdXml")
                    .to("salesforce:getSObjectWithId?format=XML&sObjectName=Line_Item__c&sObjectIdName=Name");

                // testUpsertSObject
                from("direct:upsertSObject")
                    .to("salesforce:upsertSObject?sObjectName=Line_Item__c&sObjectIdName=Name");

                from("direct:upsertSObjectXml")
                    .to("salesforce:upsertSObject?format=XML&sObjectName=Line_Item__c&sObjectIdName=Name");

                // testDeleteSObjectWithId
                from("direct:deleteSObjectWithId")
                    .to("salesforce:deleteSObjectWithId?sObjectName=Line_Item__c&sObjectIdName=Name");

                from("direct:deleteSObjectWithIdXml")
                    .to("salesforce:deleteSObjectWithId?format=XML&sObjectName=Line_Item__c&sObjectIdName=Name");

                // testGetBlobField
                from("direct:getBlobField")
                    .to("salesforce:getBlobField?sObjectName=Document&sObjectBlobFieldName=Body");

                from("direct:getBlobFieldXml")
                    .to("salesforce:getBlobField?format=XML&sObjectName=Document&sObjectBlobFieldName=Body");

                // testQuery
                from("direct:query")
                    .to("salesforce:query?sObjectQuery=SELECT name from Line_Item__c&sObjectClass=" + QueryRecordsLine_Item__c.class.getName());

                from("direct:queryXml")
                    .to("salesforce:query?format=XML&sObjectQuery=SELECT name from Line_Item__c&sObjectClass=" + QueryRecordsLine_Item__c.class.getName());

                // testSearch
                from("direct:search")
                    .to("salesforce:search?sObjectSearch=FIND {Wee}");

                from("direct:searchXml")
                    .to("salesforce:search?format=XML&sObjectSearch=FIND {Wee}");
            }
        };
    }

}
