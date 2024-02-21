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

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.context.FhirVersionEnum;
import ca.uhn.fhir.rest.api.MethodOutcome;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.fhir.api.ExtraParameters;
import org.apache.camel.component.fhir.internal.FhirApiCollection;
import org.apache.camel.component.fhir.internal.FhirCreateApiMethod;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.Registry;
import org.hl7.fhir.r4.model.HumanName;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test class for {@link org.apache.camel.component.fhir.api.FhirCreate} APIs. The class source won't be generated again
 * if the generator MOJO finds it under src/test/java.
 */
@DisabledIfSystemProperty(named = "ci.env.name", matches = "apache.org",
                          disabledReason = "Apache CI nodes are too resource constrained for this test - see CAMEL-19659")
public class FhirCreateIT extends AbstractFhirTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(FhirCreateIT.class);
    private static final String PATH_PREFIX = FhirApiCollection.getCollection().getApiName(FhirCreateApiMethod.class).getName();

    @Override
    protected CamelContext createCamelContext() throws Exception {
        //don't set serverUrl on component, use it from endpoint configration
        Registry registry = createCamelRegistry();

        if (registry != null) {
            context = new DefaultCamelContext(registry);
        } else {
            context = new DefaultCamelContext();
        }

        this.fhirContext = new FhirContext(FhirVersionEnum.R4);
        // Set proxy so that FHIR resource URLs returned by the server are using the correct host and port
        this.fhirContext.getRestfulClientFactory().setProxy(service.getHost(), service.getPort());
        this.fhirClient = this.fhirContext.newRestfulGenericClient(service.getServiceBaseURL());
        final FhirConfiguration configuration = new FhirConfiguration();

        configuration.setFhirContext(this.fhirContext);

        // add FhirComponent to Camel context
        final FhirComponent component = new FhirComponent(context);
        component.setConfiguration(configuration);
        context.addComponent("fhir", component);
        return context;
    }

    @Test
    public void testCreateResource() {
        Patient patient = new Patient().addName(new HumanName().addGiven("Vincent").setFamily("Freeman"));

        MethodOutcome result = requestBody("direct://RESOURCE", patient);

        LOG.debug("resource: {}", result);
        assertNotNull(result, "resource result");
        assertTrue(result.getCreated());
    }

    @Test
    public void testCreateStringResource() {
        Patient patient = new Patient().addName(new HumanName().addGiven("Vincent").setFamily("Freeman"));
        String patientString = this.fhirContext.newXmlParser().encodeResourceToString(patient);

        MethodOutcome result = requestBody("direct://RESOURCE_STRING", patientString);

        LOG.debug("resource: {}", result);
        assertNotNull(result, "resource result");
        assertTrue(result.getCreated());
    }

    @Test
    public void testCreateStringResourceEncodeXml() {
        Patient patient = new Patient().addName(new HumanName().addGiven("Vincent").setFamily("Freeman"));
        String patientString = this.fhirContext.newXmlParser().encodeResourceToString(patient);
        Map<String, Object> headers = new HashMap<>();
        headers.put(ExtraParameters.ENCODE_XML.getHeaderName(), Boolean.TRUE);
        MethodOutcome result = requestBodyAndHeaders("direct://RESOURCE_STRING", patientString, headers);

        LOG.debug("resource: {}", result);
        assertNotNull(result, "resource result");
        assertTrue(result.getCreated());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // test route for resource
                String serverUrl = service.getServiceBaseURL();
                from("direct://RESOURCE")
                        .to("fhir://" + PATH_PREFIX + "/resource?inBody=resource&serverUrl="
                            + serverUrl);

                // test route for resource
                from("direct://RESOURCE_STRING")
                        .to("fhir://" + PATH_PREFIX + "/resource?inBody=resourceAsString&log=true&serverUrl="
                            + serverUrl);

            }
        };
    }
}
