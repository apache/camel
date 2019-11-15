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

import ca.uhn.fhir.rest.api.MethodOutcome;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.fhir.api.ExtraParameters;
import org.apache.camel.component.fhir.internal.FhirApiCollection;
import org.apache.camel.component.fhir.internal.FhirCreateApiMethod;
import org.hl7.fhir.dstu3.model.HumanName;
import org.hl7.fhir.dstu3.model.Patient;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test class for {@link org.apache.camel.component.fhir.api.FhirCreate} APIs.
 * The class source won't be generated again if the generator MOJO finds it under src/test/java.
 */
public class FhirCreateIT extends AbstractFhirTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(FhirCreateIT.class);
    private static final String PATH_PREFIX = FhirApiCollection.getCollection().getApiName(FhirCreateApiMethod.class).getName();

    @Test
    public void testCreateResource() throws Exception {
        Patient patient = new Patient().addName(new HumanName().addGiven("Vincent").setFamily("Freeman"));

        MethodOutcome result = requestBody("direct://RESOURCE", patient);

        LOG.debug("resource: " + result);
        assertNotNull("resource result", result);
        assertTrue(result.getCreated());
    }

    @Test
    public void testCreateStringResource() throws Exception {
        Patient patient = new Patient().addName(new HumanName().addGiven("Vincent").setFamily("Freeman"));
        String patientString = this.fhirContext.newXmlParser().encodeResourceToString(patient);

        MethodOutcome result = requestBody("direct://RESOURCE_STRING", patientString);

        LOG.debug("resource: " + result);
        assertNotNull("resource result", result);
        assertTrue(result.getCreated());
    }

    @Test
    public void testCreateStringResourceEncodeXml() throws Exception {
        Patient patient = new Patient().addName(new HumanName().addGiven("Vincent").setFamily("Freeman"));
        String patientString = this.fhirContext.newXmlParser().encodeResourceToString(patient);
        Map<String, Object> headers = new HashMap<>();
        headers.put(ExtraParameters.ENCODE_XML.getHeaderName(), Boolean.TRUE);
        MethodOutcome result = requestBodyAndHeaders("direct://RESOURCE_STRING", patientString, headers);

        LOG.debug("resource: " + result);
        assertNotNull("resource result", result);
        assertTrue(result.getCreated());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                // test route for resource
                from("direct://RESOURCE")
                    .to("fhir://" + PATH_PREFIX + "/resource?inBody=resource");

                // test route for resource
                from("direct://RESOURCE_STRING")
                    .to("fhir://" + PATH_PREFIX + "/resource?inBody=resourceAsString&log=true");

            }
        };
    }
}
