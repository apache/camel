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
package org.apache.camel.component.fhir;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.fhir.api.ExtraParameters;
import org.apache.camel.component.fhir.internal.FhirApiCollection;
import org.apache.camel.component.fhir.internal.FhirHistoryApiMethod;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Test class for {@link org.apache.camel.component.fhir.api.FhirHistory} APIs. The class source won't be generated
 * again if the generator MOJO finds it under src/test/java.
 */
@DisabledIfSystemProperty(named = "ci.env.name", matches = "apache.org",
                          disabledReason = "Apache CI nodes are too resource constrained for this test - see CAMEL-19659")
public class FhirHistoryIT extends AbstractFhirTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(FhirHistoryIT.class);
    private static final String PATH_PREFIX
            = FhirApiCollection.getCollection().getApiName(FhirHistoryApiMethod.class).getName();

    @Test
    public void testOnInstance() {
        final Map<String, Object> headers = new HashMap<>();
        headers.put("CamelFhir.id", this.patient.getIdElement());
        // parameter type is Class
        headers.put("CamelFhir.returnType", Bundle.class);
        // parameter type is Integer
        headers.put("CamelFhir.count", 1);

        Bundle result = requestBodyAndHeaders("direct://ON_INSTANCE", null, headers);

        LOG.debug("onInstance: {}", result);
        assertNotNull(result, "onInstance result");
        assertEquals(1, result.getEntry().size());
    }

    @Test
    public void testOnServer() {
        Map<String, Object> headers = new HashMap<>();
        headers.put("CamelFhir.returnType", Bundle.class);
        headers.put("CamelFhir.count", 1);
        Bundle result = requestBodyAndHeaders("direct://ON_SERVER", null, headers);

        LOG.debug("onServer: {}", result);
        assertNotNull(result, "onServer result");
        assertEquals(1, result.getEntry().size());
    }

    @Test
    public void testOnType() {
        final Map<String, Object> headers = new HashMap<>();
        // parameter type is Class
        headers.put("CamelFhir.resourceType", Patient.class);
        // parameter type is Class
        headers.put("CamelFhir.returnType", Bundle.class);
        // parameter type is Integer
        headers.put("CamelFhir.count", 1);

        Bundle result = requestBodyAndHeaders("direct://ON_TYPE", null, headers);

        LOG.debug("onType: {}", result);
        assertNotNull(result, "onType result");
        assertEquals(1, result.getEntry().size());
    }

    @Test
    public void testOnTypeWithSubsetElements() {
        final Map<String, Object> headers = new HashMap<>();
        // parameter type is Class
        headers.put("CamelFhir.resourceType", Patient.class);
        // parameter type is Class
        headers.put("CamelFhir.returnType", Bundle.class);
        // parameter type is Integer
        headers.put("CamelFhir.count", 1);
        // only include the identifier and name
        headers.put(ExtraParameters.SUBSET_ELEMENTS.getHeaderName(), new String[] { "identifier", "name" });

        Bundle result = requestBodyAndHeaders("direct://ON_TYPE", null, headers);

        LOG.debug("onType: {}", result);
        assertNotNull(result, "onType result");
        assertEquals(1, result.getEntry().size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // test route for onInstance
                from("direct://ON_INSTANCE")
                        .to("fhir://" + PATH_PREFIX + "/onInstance");

                // test route for onServer
                from("direct://ON_SERVER")
                        .to("fhir://" + PATH_PREFIX + "/onServer");

                // test route for onType
                from("direct://ON_TYPE")
                        .to("fhir://" + PATH_PREFIX + "/onType");
            }
        };
    }
}
