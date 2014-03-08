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

import java.util.*;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.ComponentConfiguration;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.salesforce.dto.generated.Document;
import org.apache.camel.component.salesforce.dto.generated.Line_Item__c;
import org.apache.camel.component.salesforce.dto.generated.Merchandise__c;
import org.apache.camel.component.salesforce.dto.generated.QueryRecordsLine_Item__c;
import org.apache.camel.component.salesforce.internal.PayloadFormat;
import org.apache.camel.impl.ParameterConfiguration;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Lets test the use of the ComponentConfiguration on the Salesforce endpoint
 */
@Ignore("Must run manually with a user supplied test-salesforce-login.properties")
public class SalesforceComponentConfigurationTest extends CamelTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(SalesforceComponentConfigurationTest.class);

    protected String componentName = "salesforce123";
    protected boolean verbose = true;

    @Test
    public void testConfiguration() throws Exception {
        Component component = context().getComponent(componentName);
        ComponentConfiguration configuration = component.createComponentConfiguration();
        SortedMap<String, ParameterConfiguration> parameterConfigurationMap = configuration.getParameterConfigurationMap();
        if (verbose) {
            Set<Map.Entry<String, ParameterConfiguration>> entries = parameterConfigurationMap.entrySet();
            for (Map.Entry<String, ParameterConfiguration> entry : entries) {
                String name = entry.getKey();
                ParameterConfiguration config = entry.getValue();
                LOG.info("Has name: {} with type {}", name, config.getParameterType().getName());
            }
        }

        assertParameterConfig(configuration, "format", PayloadFormat.class);
        assertParameterConfig(configuration, "sObjectName", String.class);
        assertParameterConfig(configuration, "sObjectFields", String.class);
        assertParameterConfig(configuration, "updateTopic", boolean.class);

        configuration.setParameter("format", PayloadFormat.XML);
        configuration.setParameter("sObjectName", "Merchandise__c");
        configuration.setParameter("sObjectFields", "Description__c,Total_Inventory__c");
        configuration.setParameter("updateTopic", false);

        // operation name is base uri
        configuration.setBaseUri("getSObject");

        SalesforceEndpoint endpoint = assertIsInstanceOf(SalesforceEndpoint.class, configuration.createEndpoint());
        final SalesforceEndpointConfig endpointConfig = endpoint.getConfiguration();
        assertEquals("endpoint.format", PayloadFormat.XML, endpointConfig.getFormat());
        assertEquals("endpoint.sObjectName", "Merchandise__c", endpointConfig.getSObjectName());
        assertEquals("endpoint.sObjectFields", "Description__c,Total_Inventory__c", endpointConfig.getSObjectFields());
        assertEquals("endpoint.updateTopic", false, endpointConfig.isUpdateTopic());
    }

    public static void assertParameterConfig(ComponentConfiguration configuration, String name,
                                       Class<?> parameterType) {
        ParameterConfiguration config = configuration.getParameterConfiguration(name);
        assertNotNull("ParameterConfiguration should exist for parameter name " + name, config);
        assertEquals("ParameterConfiguration." + name + ".getName()", name, config.getName());
        assertEquals("ParameterConfiguration." + name + ".getParameterType()", parameterType,
                config.getParameterType());
    }

    @Test
    public void testEndpointCompletion() throws Exception {
        Component component = context().getComponent(componentName);
        ComponentConfiguration configuration = component.createComponentConfiguration();

        // get operation names
        assertCompletionOptions(configuration.completeEndpointPath(""),
            "getVersions", "getResources", "getGlobalObjects", "getBasicInfo", "getDescription", "getSObject",
            "createSObject", "updateSObject", "deleteSObject", "getSObjectWithId", "upsertSObject",
            "deleteSObjectWithId", "getBlobField", "query", "queryMore", "search", "createJob", "getJob",
            "closeJob", "abortJob", "createBatch", "getBatch", "getAllBatches", "getRequest", "getResults",
            "createBatchQuery", "getQueryResultIds", "getQueryResult", "[PushTopicName]"
        );

        // get filtered operation names
        assertCompletionOptions(configuration.completeEndpointPath("get"),
            "getVersions", "getResources", "getGlobalObjects", "getBasicInfo", "getDescription", "getSObject",
            "getSObjectWithId", "getBlobField", "getJob", "getBatch", "getAllBatches", "getRequest", "getResults",
            "getQueryResultIds", "getQueryResult"
        );

/* TODO support parameter completion
        // get ALL REST operation parameters
        // TODO support operation specific parameter completion
        assertCompletionOptions(configuration.completeEndpointPath("getSObject?"),
            "apiVersion", "httpClient", "format", "sObjectName", "sObjectId", "sObjectFields",
            "sObjectIdName", "sObjectIdValue", "sObjectBlobFieldName", "sObjectClass", "sObjectQuery", "sObjectSearch");

        // get filtered REST parameters
        assertCompletionOptions(configuration.completeEndpointPath("getSObject?format=XML&"),
            "apiVersion", "httpClient", "sObjectName", "sObjectId", "sObjectFields",
            "sObjectIdName", "sObjectIdValue", "sObjectBlobFieldName", "sObjectClass", "sObjectQuery", "sObjectSearch");

        // get ALL Bulk operation parameters
        // TODO support operation specific parameter completion
        assertCompletionOptions(configuration.completeEndpointPath("createJob?"),
            "apiVersion", "httpClient", "sObjectQuery", "contentType", "jobId", "batchId", "resultId");

        // get filtered Bulk operation parameters
        assertCompletionOptions(configuration.completeEndpointPath("createJob?contentType=XML&"),
            "apiVersion", "httpClient", "sObjectQuery", "jobId", "batchId", "resultId");

        // get ALL topic parameters for consumers
        assertCompletionOptions(configuration.completeEndpointPath("myTopic?"),
            "apiVersion", "httpClient", "updateTopic", "notifyForFields", "notifyForOperations");

        // get filtered topic parameters for consumers
        assertCompletionOptions(configuration.completeEndpointPath("myTopic?updateTopic=true&"),
            "apiVersion", "httpClient", "notifyForFields", "notifyForOperations");

        // get parameters from partial name
        assertCompletionOptions(configuration.completeEndpointPath("getSObject?sObject"),
            "sObjectName", "sObjectId", "sObjectFields",
            "sObjectIdName", "sObjectIdValue", "sObjectBlobFieldName", "sObjectClass", "sObjectQuery", "sObjectSearch");
*/

        // get sObjectName values, from scanned DTO packages
        assertCompletionOptions(configuration.completeEndpointPath("getSObject?sObjectName="),
            "Document", "Line_Item__c", "Merchandise__c");

        // get sObjectFields values, from scanned DTO
        assertCompletionOptions(
            configuration.completeEndpointPath("getSObject?sObjectName=Merchandise__c&sObjectFields="),
            "attributes", "Id", "OwnerId", "IsDeleted", "Name", "CreatedDate", "CreatedById",
            "LastModifiedDate", "LastModifiedById", "SystemModstamp", "LastActivityDate",
            "Description__c", "Price__c", "Total_Inventory__c");

        // get sObjectClass values, from scanned DTO packages
        assertCompletionOptions(configuration.completeEndpointPath("getSObject?sObjectClass="),
            Document.class.getName(),
            Line_Item__c.class.getName(),
            Merchandise__c.class.getName(),
            QueryRecordsLine_Item__c.class.getName());
    }

    private void assertCompletionOptions(List<String> options, final String ...args) {
        List<String> missing = new ArrayList<String>();
        for (String arg : args) {
            if (!options.remove(arg)) {
                missing.add(arg);
            }
        }
        if (!missing.isEmpty() || !options.isEmpty()) {
            fail(String.format("Missing options %s, unknown options %s",
                missing, options));
        }
    }

    protected CamelContext createCamelContext() throws Exception {
        final CamelContext camelContext = super.createCamelContext();
        final SalesforceLoginConfig loginConfig = LoginConfigHelper.getLoginConfig();
        final SalesforceComponent component = new SalesforceComponent();
        component.setLoginConfig(loginConfig);
        // set DTO package
        component.setPackages(new String[]{
            Merchandise__c.class.getPackage().getName()
        });
        camelContext.addComponent(componentName, component);
        return camelContext;
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
            }
        };
    }

}
