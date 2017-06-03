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

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.Ignore;
import org.junit.Test;

/**
 * To set-up ServiceNow for this tests:
 *
 * 1. Create a new table with
 *   - id has to be set to u_imp_incidents (name does not matter)
 *   - inherit from sys_import_set_row
 *
 * 2. Add a new field to u_imp_incidents
 *   - name short_description
 *   - id should be automatically set to u_short_description
 *
 * 3. Create a new Transform Map
 *   - source table u_imp_incidents
 *   - target table incidents
 *   - Perform auto mapping, if it does not work map each field one by one
 */
@Ignore
public class ServiceNowImportSetTest extends ServiceNowTestSupport {

    @Test
    public void testIncidentImport() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:servicenow");

        mock.reset();
        mock.expectedMessageCount(1);

        IncidentImportRequest incident = new IncidentImportRequest();
        incident.shortDescription = "test";

        template().sendBodyAndHeaders(
            "direct:servicenow",
            incident,
            kvBuilder()
                .put(ServiceNowConstants.RESOURCE, ServiceNowConstants.RESOURCE_IMPORT)
                .put(ServiceNowConstants.ACTION, ServiceNowConstants.ACTION_CREATE)
                .put(ServiceNowConstants.REQUEST_MODEL, IncidentImportRequest.class)
                .put(ServiceNowConstants.RESPONSE_MODEL, IncidentImportResponse.class)
                .put(ServiceNowParams.PARAM_TABLE_NAME, "u_imp_incident")
                .build()
        );

        mock.assertIsSatisfied();

        Message in =  mock.getExchanges().get(0).getIn();

        // Meta data
        Map<String, String> meta = in.getHeader(ServiceNowConstants.RESPONSE_META, Map.class);
        assertNotNull(meta);
        assertEquals("u_imp_incident", meta.get("staging_table"));

        // Incidents
        List<IncidentImportResponse> responses = in.getBody(List.class);
        assertNotNull(responses);
        assertEquals(1, responses.size());
        assertEquals("inserted", responses.get(0).status);
        assertEquals("imp_incidents", responses.get(0).transformMap);
        assertEquals("incident", responses.get(0).table);
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
        @JsonProperty("u_short_description")
        public String shortDescription;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private static final class IncidentImportResponse {
        @JsonProperty("transform_map")
        public String transformMap;
        @JsonProperty("table")
        public String table;
        @JsonProperty("display_name")
        public String displayName;
        @JsonProperty("display_value")
        public String displayValue;
        @JsonProperty("record_link")
        public String recordLink;
        @JsonProperty("status")
        public String status;
        @JsonProperty("sys_id")
        public String sysId;
    }
}
