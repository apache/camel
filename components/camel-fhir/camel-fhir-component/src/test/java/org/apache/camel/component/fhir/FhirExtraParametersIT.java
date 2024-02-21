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
import org.apache.camel.component.fhir.internal.FhirSearchApiMethod;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Test class for {@link org.apache.camel.component.fhir.api.FhirSearch} APIs. The class source won't be generated again
 * if the generator MOJO finds it under src/test/java.
 */
@DisabledIfSystemProperty(named = "ci.env.name", matches = "apache.org",
                          disabledReason = "Apache CI nodes are too resource constrained for this test - see CAMEL-19659")
public class FhirExtraParametersIT extends AbstractFhirTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(FhirExtraParametersIT.class);
    private static final String PATH_PREFIX = FhirApiCollection.getCollection().getApiName(FhirSearchApiMethod.class).getName();

    @Test
    public void testEncodeRequestToXml() {
        final Map<String, Object> headers = new HashMap<>();
        // encode request to XML
        headers.put(ExtraParameters.ENCODE_XML.getHeaderName(), Boolean.TRUE);
        String url = "Patient?given=Vincent&family=Freeman&_format=json";

        Bundle result = requestBodyAndHeaders("direct://SEARCH_BY_URL", url, headers);

        LOG.debug("searchByUrl: {}", result);
        assertNotNull(result, "searchByUrl result");
        Patient patient = (Patient) result.getEntry().get(0).getResource();
        assertNotNull(patient);
        assertEquals("Freeman", patient.getName().get(0).getFamily());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // test route for searchByUrl
                from("direct://SEARCH_BY_URL")
                        .to("fhir://" + PATH_PREFIX + "/searchByUrl?inBody=url");

            }
        };
    }
}
