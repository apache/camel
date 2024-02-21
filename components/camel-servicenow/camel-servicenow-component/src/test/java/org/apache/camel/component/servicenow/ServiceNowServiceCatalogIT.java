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
import java.util.Map;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

@EnabledIfEnvironmentVariable(named = "SERVICENOW_INSTANCE", matches = ".*",
                              disabledReason = "Service now instance was not provided")
public class ServiceNowServiceCatalogIT extends ServiceNowITSupport {
    @Produce("direct:servicenow")
    ProducerTemplate template;

    @Test
    public void testRetrieveServiceCatalogsAndCategories() {
        List<Map<?, ?>> result1 = template.requestBodyAndHeaders(
                "direct:servicenow",
                null,
                kvBuilder()
                        .put(ServiceNowConstants.RESOURCE, ServiceNowConstants.RESOURCE_SERVICE_CATALOG)
                        .put(ServiceNowConstants.ACTION, ServiceNowConstants.ACTION_RETRIEVE)
                        .build(),
                List.class);

        assertFalse(result1.isEmpty());

        List<Map<?, ?>> result2 = template.requestBodyAndHeaders(
                "direct:servicenow",
                null,
                kvBuilder()
                        .put(ServiceNowConstants.RESOURCE, ServiceNowConstants.RESOURCE_SERVICE_CATALOG)
                        .put(ServiceNowConstants.ACTION, ServiceNowConstants.ACTION_RETRIEVE)
                        .put(ServiceNowConstants.ACTION_SUBJECT, ServiceNowConstants.ACTION_SUBJECT_CATEGORIES)
                        .put(ServiceNowParams.PARAM_SYS_ID, result1.get(0).get("sys_id"))
                        .build(),
                List.class);

        assertFalse(result2.isEmpty());
    }

    @Test
    public void testWrongSubject() {
        final Map<String, Object> invalid = kvBuilder()
                .put(ServiceNowConstants.RESOURCE, ServiceNowConstants.RESOURCE_SERVICE_CATALOG)
                .put(ServiceNowConstants.ACTION, ServiceNowConstants.ACTION_RETRIEVE)
                .put(ServiceNowConstants.ACTION_SUBJECT, "Invalid")
                .build();

        assertThrows(CamelExecutionException.class,
                () -> template.requestBodyAndHeaders(
                        "direct:servicenow",
                        null,
                        invalid,
                        List.class));
    }

    // *************************************************************************
    //
    // *************************************************************************

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:servicenow")
                        .to("servicenow:{{env:SERVICENOW_INSTANCE}}")
                        .to("log:org.apache.camel.component.servicenow?level=INFO&showAll=true")
                        .to("mock:servicenow");
            }
        };
    }
}
