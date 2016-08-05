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

package org.apache.camel.component.servicenow;

import java.util.UUID;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class ServiceNowTest extends ServiceNowTestSupport {

    @Test
    public void testExceptions() throws Exception {
        // 404
        try {
            template().sendBodyAndHeaders(
                "direct:servicenow",
                null,
                new KVBuilder()
                    .put(ServiceNowConstants.RESOURCE, "table")
                    .put(ServiceNowConstants.ACTION, ServiceNowConstants.ACTION_RETRIEVE)
                    .put(ServiceNowConstants.SYSPARM_QUERY, "number=" + UUID.randomUUID().toString())
                    .put(ServiceNowConstants.TABLE, "incident")
                    .build()
            );
        } catch (CamelExecutionException e) {
            assertTrue(e.getCause() instanceof ServiceNowException);

            ServiceNowException sne = (ServiceNowException)e.getCause();
            assertEquals("failure", sne.getStatus());
            assertTrue(sne.getMessage().contains("No Record found"));
            assertTrue(sne.getDetail().contains("Records matching query not found"));
        }

        // 400
        try {
            template().sendBodyAndHeaders(
                "direct:servicenow",
                null,
                new KVBuilder()
                    .put(ServiceNowConstants.RESOURCE, "table")
                    .put(ServiceNowConstants.ACTION, ServiceNowConstants.ACTION_RETRIEVE)
                    .put(ServiceNowConstants.SYSPARM_QUERY, "number=" + UUID.randomUUID().toString())
                    .put(ServiceNowConstants.TABLE, "notExistingTable")
                    .build()
            );
        } catch (CamelExecutionException e) {
            assertTrue(e.getCause() instanceof ServiceNowException);

            ServiceNowException sne = (ServiceNowException)e.getCause();
            assertEquals("failure", sne.getStatus());
            assertTrue(sne.getMessage().contains("Invalid table notExistingTable"));
            assertNull(sne.getDetail());
        }
    }

    @Test
    public void testBodyMismatch() throws Exception {
        try {
            template().sendBodyAndHeaders(
                "direct:servicenow",
                "NotAnIncidentObject",
                new KVBuilder()
                    .put(ServiceNowConstants.RESOURCE, "table")
                    .put(ServiceNowConstants.ACTION, ServiceNowConstants.ACTION_CREATE)
                    .put(ServiceNowConstants.TABLE, "incident")
                    .build()
            );

            fail("Should fail as body is not compatible with model defined in route for table incident");
        } catch (CamelExecutionException e) {
            assertTrue(e.getCause() instanceof IllegalArgumentException);
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
                        + "?userName={{env:SERVICENOW_USERNAME}}"
                        + "&password={{env:SERVICENOW_PASSWORD}}"
                        //+ "&oauthClientId={{env:SERVICENOW_OAUTH2_CLIENT_ID}}"
                        //+ "&oauthClientSecret={{env:SERVICENOW_OAUTH2_CLIENT_SECRET}}"
                        + "&model.incident=org.apache.camel.component.servicenow.model.Incident")
                    .to("log:org.apache.camel.component.servicenow?level=INFO&showAll=true")
                    .to("mock:servicenow");
            }
        };
    }
}
