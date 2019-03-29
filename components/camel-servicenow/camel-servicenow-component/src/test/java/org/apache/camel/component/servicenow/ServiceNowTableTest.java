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
package org.apache.camel.component.servicenow;

import java.util.List;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.servicenow.model.Incident;
import org.apache.camel.component.servicenow.model.IncidentWithParms;
import org.junit.Test;

public class ServiceNowTableTest extends ServiceNowTestSupport {

    @Test
    public void testRetrieveSome() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:servicenow");
        mock.expectedMessageCount(1);

        template().sendBodyAndHeaders(
            "direct:servicenow",
            null,
            kvBuilder()
                .put(ServiceNowConstants.RESOURCE, "table")
                .put(ServiceNowConstants.ACTION, ServiceNowConstants.ACTION_RETRIEVE)
                .put(ServiceNowParams.SYSPARM_LIMIT, 10)
                .put(ServiceNowParams.PARAM_TABLE_NAME, "incident")
                .build()
        );

        mock.assertIsSatisfied();

        Exchange exchange = mock.getExchanges().get(0);
        List<Incident> items = exchange.getIn().getBody(List.class);

        assertNotNull(items);
        assertTrue(items.size() <= 10);
        assertNotNull(exchange.getIn().getHeader(ServiceNowConstants.RESPONSE_TYPE));
        assertNotNull(exchange.getIn().getHeader(ServiceNowConstants.OFFSET_FIRST));
        assertNotNull(exchange.getIn().getHeader(ServiceNowConstants.OFFSET_NEXT));
        assertNotNull(exchange.getIn().getHeader(ServiceNowConstants.OFFSET_LAST));
    }

    @Test
    public void testRetrieveSomeWithParams() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:servicenow");
        mock.expectedMessageCount(1);

        template().sendBodyAndHeaders(
            "direct:servicenow",
            null,
            kvBuilder()
                .put(ServiceNowConstants.RESOURCE, "table")
                .put(ServiceNowConstants.ACTION, ServiceNowConstants.ACTION_RETRIEVE)
                .put(ServiceNowParams.SYSPARM_LIMIT, 10)
                .put(ServiceNowParams.SYSPARM_EXCLUDE_REFERENCE_LINK, false)
                .put(ServiceNowParams.PARAM_TABLE_NAME, "incident")
                .put(ServiceNowConstants.MODEL, IncidentWithParms.class)
                .build()
        );

        mock.assertIsSatisfied();

        Exchange exchange = mock.getExchanges().get(0);
        List<Incident> items = exchange.getIn().getBody(List.class);

        assertNotNull(items);
        assertFalse(items.isEmpty());
        assertTrue(items.size() <= 10);
        assertNotNull(exchange.getIn().getHeader(ServiceNowConstants.RESPONSE_TYPE));
        assertNotNull(exchange.getIn().getHeader(ServiceNowConstants.OFFSET_FIRST));
        assertNotNull(exchange.getIn().getHeader(ServiceNowConstants.OFFSET_NEXT));
        assertNotNull(exchange.getIn().getHeader(ServiceNowConstants.OFFSET_LAST));
    }

    @Test
    public void testRetrieveSomeWithDefaults() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:servicenow-defaults");
        mock.expectedMessageCount(1);

        template().sendBodyAndHeaders(
            "direct:servicenow-defaults",
            null,
            kvBuilder()
                .put(ServiceNowConstants.ACTION, ServiceNowConstants.ACTION_RETRIEVE)
                .put(ServiceNowParams.SYSPARM_LIMIT, 10)
                .build()
        );

        mock.assertIsSatisfied();

        Exchange exchange = mock.getExchanges().get(0);
        List<Incident> items = exchange.getIn().getBody(List.class);

        assertNotNull(items);
        assertTrue(items.size() <= 10);
    }

    @Test
    public void testIncidentWorkflow() throws Exception {

        Incident incident = null;
        String sysId;
        String number;
        MockEndpoint mock = getMockEndpoint("mock:servicenow");

        // ************************
        // Create incident
        // ************************

        {
            mock.reset();
            mock.expectedMessageCount(1);

            incident = new Incident();
            incident.setDescription("my incident");
            incident.setShortDescription("An incident");
            incident.setSeverity(1);
            incident.setImpact(1);

            template().sendBodyAndHeaders(
                "direct:servicenow",
                incident,
                kvBuilder()
                    .put(ServiceNowConstants.RESOURCE, "table")
                    .put(ServiceNowConstants.ACTION, ServiceNowConstants.ACTION_CREATE)
                    .put(ServiceNowParams.PARAM_TABLE_NAME, "incident")
                    .build()
            );

            mock.assertIsSatisfied();

            incident = mock.getExchanges().get(0).getIn().getBody(Incident.class);
            sysId = incident.getId();
            number = incident.getNumber();

            LOGGER.info("****************************************************");
            LOGGER.info(" Incident created");
            LOGGER.info("  sysid  = {}", sysId);
            LOGGER.info("  number = {}", number);
            LOGGER.info("****************************************************");
        }

        // ************************
        // Search for the incident
        // ************************

        {
            LOGGER.info("Search the record {}", sysId);

            mock.reset();
            mock.expectedMessageCount(1);

            template().sendBodyAndHeaders(
                "direct:servicenow",
                null,
                kvBuilder()
                    .put(ServiceNowConstants.RESOURCE, "table")
                    .put(ServiceNowConstants.ACTION, ServiceNowConstants.ACTION_RETRIEVE)
                    .put(ServiceNowParams.PARAM_TABLE_NAME, "incident")
                    .put(ServiceNowParams.SYSPARM_QUERY, "number=" + number)
                    .build()
            );

            mock.assertIsSatisfied();

            List<Incident> incidents = mock.getExchanges().get(0).getIn().getBody(List.class);
            assertEquals(1, incidents.size());
            assertEquals(number, incidents.get(0).getNumber());
            assertEquals(sysId, incidents.get(0).getId());
        }

        // ************************
        // Modify the incident
        // ************************

        {
            LOGGER.info("Update the record {}", sysId);

            mock.reset();
            mock.expectedMessageCount(1);

            incident = new Incident();
            incident.setDescription("my incident");
            incident.setShortDescription("The incident");
            incident.setSeverity(2);
            incident.setImpact(3);

            template().sendBodyAndHeaders(
                "direct:servicenow",
                incident,
                kvBuilder()
                    .put(ServiceNowConstants.RESOURCE, "table")
                    .put(ServiceNowConstants.ACTION, ServiceNowConstants.ACTION_MODIFY)
                    .put(ServiceNowParams.PARAM_TABLE_NAME, "incident")
                    .put(ServiceNowParams.PARAM_SYS_ID, sysId)
                    .build()
            );

            mock.assertIsSatisfied();

            incident = mock.getExchanges().get(0).getIn().getBody(Incident.class);
            assertEquals(number, incident.getNumber());
            assertEquals(2, incident.getSeverity());
            assertEquals(3, incident.getImpact());
            assertEquals("The incident", incident.getShortDescription());
        }

        // ************************
        // Retrieve it via query
        // ************************

        {
            LOGGER.info("Retrieve the record {}", sysId);

            mock.reset();
            mock.expectedMessageCount(1);

            template().sendBodyAndHeaders(
                "direct:servicenow",
                null,
                kvBuilder()
                    .put(ServiceNowConstants.RESOURCE, "table")
                    .put(ServiceNowConstants.ACTION, ServiceNowConstants.ACTION_RETRIEVE)
                    .put(ServiceNowParams.PARAM_TABLE_NAME, "incident")
                    .put(ServiceNowParams.SYSPARM_QUERY, "number=" + number)
                    .build()
            );

            mock.assertIsSatisfied();

            List<Incident> incidents = mock.getExchanges().get(0).getIn().getBody(List.class);
            assertEquals(1, incidents.size());
            assertEquals(number, incidents.get(0).getNumber());
            assertEquals(sysId, incidents.get(0).getId());
            assertEquals(2, incidents.get(0).getSeverity());
            assertEquals(3, incidents.get(0).getImpact());
            assertEquals("The incident", incidents.get(0).getShortDescription());
        }

        // ************************
        // Retrieve by sys id
        // ************************

        {
            LOGGER.info("Search the record {}", sysId);

            mock.reset();
            mock.expectedMessageCount(1);

            template().sendBodyAndHeaders(
                "direct:servicenow",
                null,
                kvBuilder()
                    .put(ServiceNowConstants.RESOURCE, "table")
                    .put(ServiceNowConstants.ACTION, ServiceNowConstants.ACTION_RETRIEVE)
                    .put(ServiceNowParams.PARAM_TABLE_NAME, "incident")
                    .put(ServiceNowParams.PARAM_SYS_ID, sysId)
                    .build()
            );

            mock.assertIsSatisfied();

            incident = mock.getExchanges().get(0).getIn().getBody(Incident.class);
            assertEquals(2, incident.getSeverity());
            assertEquals(3, incident.getImpact());
            assertEquals("The incident", incident.getShortDescription());
            assertEquals(number, incident.getNumber());
        }

        // ************************
        // Delete it
        // ************************

        {
            LOGGER.info("Delete the record {}", sysId);

            mock.reset();
            mock.expectedMessageCount(1);

            template().sendBodyAndHeaders(
                "direct:servicenow",
                null,
                kvBuilder()
                    .put(ServiceNowConstants.RESOURCE, "table")
                    .put(ServiceNowConstants.ACTION, ServiceNowConstants.ACTION_DELETE)
                    .put(ServiceNowParams.PARAM_TABLE_NAME, "incident")
                    .put(ServiceNowParams.PARAM_SYS_ID, sysId)
                    .build()
            );

            mock.assertIsSatisfied();
        }

        // ************************
        // Retrieve by id, should fail
        // ************************

        {
            LOGGER.info("Find the record {}, should fail", sysId);

            try {
                template().sendBodyAndHeaders(
                    "direct:servicenow",
                    null,
                    kvBuilder()
                        .put(ServiceNowConstants.RESOURCE, "table")
                        .put(ServiceNowConstants.ACTION, ServiceNowConstants.ACTION_RETRIEVE)
                        .put(ServiceNowParams.PARAM_SYS_ID, sysId)
                        .put(ServiceNowParams.PARAM_TABLE_NAME, "incident")
                        .build()
                );

                fail("Record " + number + " should have been deleted");
            } catch (CamelExecutionException e) {
                assertTrue(e.getCause() instanceof ServiceNowException);
                // we are good
            }
        }
    }

    // *************************************************************************
    //
    // *************************************************************************

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:servicenow")
                    .to("servicenow:{{env:SERVICENOW_INSTANCE}}"
                        + "?model.incident=org.apache.camel.component.servicenow.model.Incident")
                    .to("log:org.apache.camel.component.servicenow?level=INFO&showAll=true")
                    .to("mock:servicenow");
                from("direct:servicenow-defaults")
                    .to("servicenow:{{env:SERVICENOW_INSTANCE}}"
                        + "?model.incident=org.apache.camel.component.servicenow.model.Incident"
                        + "&resource=table"
                        + "&table=incident")
                    .to("log:org.apache.camel.component.servicenow?level=INFO&showAll=true")
                    .to("mock:servicenow-defaults");
            }
        };
    }
}
