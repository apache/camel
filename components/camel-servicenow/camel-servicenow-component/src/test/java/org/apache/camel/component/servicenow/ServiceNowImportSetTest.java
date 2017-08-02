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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.component.servicenow.model.ImportSetResult;
import org.apache.camel.component.servicenow.model.Incident;
import org.junit.Ignore;
import org.junit.Test;

/**
 * To set-up ServiceNow for this tests:
 *
 * 1. Create a new web service named u_test_imp_incident targeting incident table
 * 2. Create a mapping (automatic)
 */
@Ignore
public class ServiceNowImportSetTest extends ServiceNowTestSupport {

    @Test
    public void testIncidentImport() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:servicenow");

        mock.reset();
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived(ServiceNowConstants.RESPONSE_TYPE, ArrayList.class);

        IncidentImportRequest incident = new IncidentImportRequest();
        incident.description = UUID.randomUUID().toString();
        incident.correlationId = UUID.randomUUID().toString();

        template().sendBodyAndHeaders(
            "direct:servicenow",
            incident,
            kvBuilder()
                .put(ServiceNowConstants.RESOURCE, ServiceNowConstants.RESOURCE_IMPORT)
                .put(ServiceNowConstants.ACTION, ServiceNowConstants.ACTION_CREATE)
                .put(ServiceNowConstants.REQUEST_MODEL, IncidentImportRequest.class)
                .put(ServiceNowConstants.RESPONSE_MODEL, ImportSetResult.class)
                .put(ServiceNowParams.PARAM_TABLE_NAME, "u_test_imp_incident")
                .build()
        );

        mock.assertIsSatisfied();

        Message in =  mock.getExchanges().get(0).getIn();

        // Meta data
        Map<String, String> meta = in.getHeader(ServiceNowConstants.RESPONSE_META, Map.class);
        assertNotNull(meta);
        assertEquals("u_test_imp_incident", meta.get("staging_table"));

        // Incidents
        List<ImportSetResult> responses = in.getBody(List.class);
        assertNotNull(responses);
        assertEquals(1, responses.size());
        assertEquals("inserted", responses.get(0).getStatus());
        assertEquals("test_imp_incident", responses.get(0).getTransformMap());
        assertEquals("incident", responses.get(0).getTable());
    }

    @Test
    public void testIncidentImportWithRetrieve() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:servicenow");

        mock.reset();
        mock.expectedMessageCount(1);
        mock.expectedHeaderReceived(ServiceNowConstants.RESPONSE_TYPE, Incident.class);

        IncidentImportRequest incident = new IncidentImportRequest();
        incident.description = UUID.randomUUID().toString();

        template().sendBodyAndHeaders(
            "direct:servicenow",
            incident,
            kvBuilder()
                .put(ServiceNowConstants.RESOURCE, ServiceNowConstants.RESOURCE_IMPORT)
                .put(ServiceNowConstants.ACTION, ServiceNowConstants.ACTION_CREATE)
                .put(ServiceNowConstants.REQUEST_MODEL, IncidentImportRequest.class)
                .put(ServiceNowConstants.RESPONSE_MODEL, Incident.class)
                .put(ServiceNowConstants.RETRIEVE_TARGET_RECORD, true)
                .put(ServiceNowParams.PARAM_TABLE_NAME, "u_test_imp_incident")
                .build()
        );

        mock.assertIsSatisfied();

        Incident response = mock.getExchanges().get(0).getIn().getBody(Incident.class);
        assertNotNull(response);
        assertEquals(incident.description, response.getDescription());
        assertNotNull(response.getNumber());
        assertNotNull(response.getId());
    }

    // *************************************************************************
    //
    // *************************************************************************

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                from("direct:servicenow")
                    .to("servicenow:{{env:SERVICENOW_INSTANCE}}")
                    .to("log:org.apache.camel.component.servicenow?level=INFO&showAll=true")
                    .to("mock:servicenow");
            }
        };
    }

    // *************************************************************************
    //
    // *************************************************************************

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private static final class IncidentImportRequest {
        @JsonProperty("description")
        public String description;
        @JsonProperty("correlation_id")
        public String correlationId;
    }
}
