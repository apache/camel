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
package org.apache.camel.component.fhir;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import ca.uhn.fhir.rest.api.SummaryEnum;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.fhir.api.ExtraParameters;
import org.apache.camel.component.fhir.internal.FhirApiCollection;
import org.apache.camel.component.fhir.internal.FhirTransactionApiMethod;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.HumanName;
import org.hl7.fhir.dstu3.model.Patient;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test class for {@link org.apache.camel.component.fhir.api.FhirTransaction} APIs.
 * The class source won't be generated again if the generator MOJO finds it under src/test/java.
 */
public class FhirTransactionIT extends AbstractFhirTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(FhirTransactionIT.class);
    private static final String PATH_PREFIX = FhirApiCollection.getCollection().getApiName(FhirTransactionApiMethod.class).getName();

    @Test
    public void testWithBundle() throws Exception {
        // using org.hl7.fhir.instance.model.api.IBaseBundle message body for single parameter "bundle"
        Bundle result = requestBody("direct://WITH_BUNDLE", createTransactionBundle());

        assertNotNull("withBundle result", result);
        assertTrue(result.getEntry().get(0).getResponse().getStatus().contains("Created"));
        LOG.debug("withBundle: " + result);
    }

    @Test
    public void testWithStringBundle() throws Exception {
        Bundle transactionBundle = createTransactionBundle();
        String stringBundle = fhirContext.newJsonParser().encodeResourceToString(transactionBundle);

        // using String message body for single parameter "sBundle"
        final String result = requestBody("direct://WITH_STRING_BUNDLE", stringBundle);

        assertNotNull("withBundle result", result);
        assertTrue(result.contains("Bundle"));
        LOG.debug("withBundle: " + result);
    }

    @Test
    public void testWithResources() throws Exception {
        Patient oscar = new Patient().addName(new HumanName().addGiven("Oscar").setFamily("Peterson"));
        Patient bobbyHebb = new Patient().addName(new HumanName().addGiven("Bobby").setFamily("Hebb"));
        List<IBaseResource> patients = new ArrayList<>(2);
        patients.add(oscar);
        patients.add(bobbyHebb);

        // using java.util.List message body for single parameter "resources"
        List<IBaseResource> result = requestBody("direct://WITH_RESOURCES", patients);

        assertNotNull("withResources result", result);
        LOG.debug("withResources: " + result);
        assertTrue(result.size() == 2);
    }

    @Test
    public void testWithResourcesSummaryEnum() throws Exception {
        Patient oscar = new Patient().addName(new HumanName().addGiven("Oscar").setFamily("Peterson"));
        Patient bobbyHebb = new Patient().addName(new HumanName().addGiven("Bobby").setFamily("Hebb"));
        List<IBaseResource> patients = new ArrayList<>(2);
        patients.add(oscar);
        patients.add(bobbyHebb);
        final Map<String, Object> headers = new HashMap<>();
        headers.put(ExtraParameters.SUMMARY_ENUM.getHeaderName(), SummaryEnum.DATA);

        // using java.util.List message body for single parameter "resources"
        List<IBaseResource> result = requestBodyAndHeaders("direct://WITH_RESOURCES", patients, headers);

        assertNotNull("withResources result", result);
        LOG.debug("withResources: " + result);
        assertTrue(result.size() == 2);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                // test route for withBundle
                from("direct://WITH_BUNDLE")
                    .to("fhir://" + PATH_PREFIX + "/withBundle?inBody=bundle");

                // test route for withBundle
                from("direct://WITH_STRING_BUNDLE")
                    .to("fhir://" + PATH_PREFIX + "/withBundle?inBody=stringBundle");

                // test route for withResources
                from("direct://WITH_RESOURCES")
                    .to("fhir://" + PATH_PREFIX + "/withResources?inBody=resources");

            }
        };
    }

    private Bundle createTransactionBundle() {
        Bundle input = new Bundle();
        input.setType(Bundle.BundleType.TRANSACTION);
        input.addEntry()
            .setResource(new Patient().addName(new HumanName().addGiven("Art").setFamily("Tatum")))
            .getRequest()
            .setMethod(Bundle.HTTPVerb.POST);
        return input;
    }

}
