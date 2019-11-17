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
import org.apache.camel.component.fhir.internal.FhirCapabilitiesApiMethod;
import org.hl7.fhir.dstu3.model.CapabilityStatement;
import org.hl7.fhir.dstu3.model.Enumerations;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test class for {@link org.apache.camel.component.fhir.api.FhirCapabilities} APIs.
 * The class source won't be generated again if the generator MOJO finds it under src/test/java.
 */
public class FhirCapabilitiesIT extends AbstractFhirTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(FhirCapabilitiesIT.class);
    private static final String PATH_PREFIX = FhirApiCollection.getCollection().getApiName(FhirCapabilitiesApiMethod.class).getName();

    @Test
    public void testOfType() throws Exception {
        org.hl7.fhir.instance.model.api.IBaseConformance result = requestBody("direct://OF_TYPE", CapabilityStatement.class);

        LOG.debug("ofType: " + result);
        assertNotNull("ofType result", result);
        assertEquals(Enumerations.PublicationStatus.ACTIVE, ((CapabilityStatement)result).getStatus());
    }

    @Test
    public void testEncodeJSON() throws Exception {
        Map<String, Object> headers = new HashMap<>();
        headers.put(ExtraParameters.ENCODE_JSON.getHeaderName(), Boolean.TRUE);

        org.hl7.fhir.instance.model.api.IBaseConformance result = requestBodyAndHeaders("direct://OF_TYPE", CapabilityStatement.class, headers);

        LOG.debug("ofType: " + result);
        assertNotNull("ofType result", result);
        assertEquals(Enumerations.PublicationStatus.ACTIVE, ((CapabilityStatement)result).getStatus());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                // test route for ofType
                from("direct://OF_TYPE")
                    .to("fhir://" + PATH_PREFIX + "/ofType?inBody=type&log=true");

            }
        };
    }
}
