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

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import ca.uhn.fhir.rest.api.MethodOutcome;
import ca.uhn.fhir.rest.api.PreferReturnEnum;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.fhir.internal.FhirApiCollection;
import org.apache.camel.component.fhir.internal.FhirUpdateApiMethod;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Test class for {@link org.apache.camel.component.fhir.api.FhirUpdate} APIs. The class source won't be generated again
 * if the generator MOJO finds it under src/test/java.
 */
@DisabledIfSystemProperty(named = "ci.env.name", matches = "apache.org",
                          disabledReason = "Apache CI nodes are too resource constrained for this test - see CAMEL-19659")
public class FhirUpdateIT extends AbstractFhirTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(FhirUpdateIT.class);
    private static final String PATH_PREFIX = FhirApiCollection.getCollection().getApiName(FhirUpdateApiMethod.class).getName();

    @Test
    public void testResource() throws Exception {
        Date date = new SimpleDateFormat("yyyy-MM-dd").parse("1998-04-29");
        assertNotEquals(date, patient.getBirthDate());
        this.patient.setBirthDate(date);
        final Map<String, Object> headers = new HashMap<>();
        // parameter type is org.hl7.fhir.instance.model.api.IBaseResource
        headers.put("CamelFhir.resource", this.patient);
        // parameter type is org.hl7.fhir.instance.model.api.IIdType
        headers.put("CamelFhir.id", this.patient.getIdElement());
        // parameter type is ca.uhn.fhir.rest.api.PreferReturnEnum
        headers.put("CamelFhir.preferReturn", PreferReturnEnum.REPRESENTATION);

        MethodOutcome result = requestBodyAndHeaders("direct://RESOURCE", null, headers);

        assertNotNull(result, "resource result");
        LOG.debug("resource: {}", result);
        assertEquals(date, ((Patient) result.getResource()).getBirthDate(), "Birth date not updated!");
    }

    @Test
    public void testResourceNoId() throws Exception {
        Date date = new SimpleDateFormat("yyyy-MM-dd").parse("1998-04-29");
        assertNotEquals(date, patient.getBirthDate());
        this.patient.setBirthDate(date);
        final Map<String, Object> headers = new HashMap<>();
        // parameter type is org.hl7.fhir.instance.model.api.IBaseResource
        headers.put("CamelFhir.resource", this.patient);
        // parameter type is ca.uhn.fhir.rest.api.PreferReturnEnum
        headers.put("CamelFhir.preferReturn", PreferReturnEnum.REPRESENTATION);

        MethodOutcome result = requestBodyAndHeaders("direct://RESOURCE", null, headers);

        assertNotNull(result, "resource result");
        LOG.debug("resource: {}", result);
        assertEquals(date, ((Patient) result.getResource()).getBirthDate(), "Birth date not updated!");
    }

    @Test
    public void testResourceStringId() throws Exception {
        Date date = new SimpleDateFormat("yyyy-MM-dd").parse("1998-04-29");
        assertNotEquals(date, patient.getBirthDate());
        this.patient.setBirthDate(date);
        final Map<String, Object> headers = new HashMap<>();
        // parameter type is org.hl7.fhir.instance.model.api.IBaseResource
        headers.put("CamelFhir.resource", this.patient);
        // parameter type is org.hl7.fhir.instance.model.api.IIdType
        headers.put("CamelFhir.stringId", this.patient.getIdElement().getIdPart());
        // parameter type is ca.uhn.fhir.rest.api.PreferReturnEnum
        headers.put("CamelFhir.preferReturn", PreferReturnEnum.REPRESENTATION);

        MethodOutcome result = requestBodyAndHeaders("direct://RESOURCE_WITH_STRING_ID", null, headers);

        assertNotNull(result, "resource result");
        LOG.debug("resource: {}", result);
        assertEquals(date, ((Patient) result.getResource()).getBirthDate(), "Birth date not updated!");
    }

    @Test
    public void testResourceAsString() throws Exception {
        Date date = new SimpleDateFormat("yyyy-MM-dd").parse("1998-04-29");
        assertNotEquals(date, patient.getBirthDate());
        this.patient.setBirthDate(date);
        final Map<String, Object> headers = new HashMap<>();
        // parameter type is org.hl7.fhir.instance.model.api.IBaseResource
        headers.put("CamelFhir.resourceAsString", this.fhirContext.newJsonParser().encodeResourceToString(this.patient));
        // parameter type is org.hl7.fhir.instance.model.api.IIdType
        headers.put("CamelFhir.id", this.patient.getIdElement());
        // parameter type is ca.uhn.fhir.rest.api.PreferReturnEnum
        headers.put("CamelFhir.preferReturn", PreferReturnEnum.REPRESENTATION);

        MethodOutcome result = requestBodyAndHeaders("direct://RESOURCE_AS_STRING", null, headers);

        assertNotNull(result, "resource result");
        LOG.debug("resource: {}", result);
        assertEquals(date, ((Patient) result.getResource()).getBirthDate(), "Birth date not updated!");
    }

    @Test
    public void testResourceAsStringWithStringId() throws Exception {
        Date date = new SimpleDateFormat("yyyy-MM-dd").parse("1998-04-29");
        assertNotEquals(date, patient.getBirthDate());
        this.patient.setBirthDate(date);
        final Map<String, Object> headers = new HashMap<>();
        // parameter type is org.hl7.fhir.instance.model.api.IBaseResource
        headers.put("CamelFhir.resourceAsString", this.fhirContext.newJsonParser().encodeResourceToString(this.patient));
        // parameter type is org.hl7.fhir.instance.model.api.IIdType
        headers.put("CamelFhir.stringId", this.patient.getIdElement().getIdPart());
        // parameter type is ca.uhn.fhir.rest.api.PreferReturnEnum
        headers.put("CamelFhir.preferReturn", PreferReturnEnum.REPRESENTATION);

        MethodOutcome result = requestBodyAndHeaders("direct://RESOURCE_AS_STRING_WITH_STRING_ID", null, headers);

        assertNotNull(result, "resource result");
        LOG.debug("resource: {}", result);
        assertEquals(date, ((Patient) result.getResource()).getBirthDate(), "Birth date not updated!");
    }

    @Test
    public void testResourceBySearchUrl() throws Exception {
        Date date = new SimpleDateFormat("yyyy-MM-dd").parse("1998-04-29");
        assertNotEquals(date, patient.getBirthDate());
        this.patient.setBirthDate(date);
        String url = "Patient?" + Patient.SP_RES_ID + '=' + patient.getIdPart();
        final Map<String, Object> headers = new HashMap<>();
        // parameter type is org.hl7.fhir.instance.model.api.IBaseResource
        headers.put("CamelFhir.resource", this.patient);
        // parameter type is String
        headers.put("CamelFhir.url", url);
        // parameter type is ca.uhn.fhir.rest.api.PreferReturnEnum
        headers.put("CamelFhir.preferReturn", PreferReturnEnum.REPRESENTATION);

        MethodOutcome result = requestBodyAndHeaders("direct://RESOURCE_BY_SEARCH_URL", null, headers);

        assertNotNull(result, "resource result");
        LOG.debug("resource: {}", result);
        assertEquals(date, ((Patient) result.getResource()).getBirthDate(), "Birth date not updated!");
    }

    @Test
    public void testResourceBySearchUrlAndResourceAsString() throws Exception {
        Date date = new SimpleDateFormat("yyyy-MM-dd").parse("1998-04-29");
        assertNotEquals(date, patient.getBirthDate());
        this.patient.setBirthDate(date);
        String url = "Patient?" + Patient.SP_RES_ID + '=' + patient.getIdPart();
        final Map<String, Object> headers = new HashMap<>();
        // parameter type is org.hl7.fhir.instance.model.api.IBaseResource
        headers.put("CamelFhir.resourceAsString", this.fhirContext.newJsonParser().encodeResourceToString(this.patient));
        // parameter type is String
        headers.put("CamelFhir.url", url);
        // parameter type is ca.uhn.fhir.rest.api.PreferReturnEnum
        headers.put("CamelFhir.preferReturn", PreferReturnEnum.REPRESENTATION);

        MethodOutcome result = requestBodyAndHeaders("direct://RESOURCE_BY_SEARCH_URL_AND_RESOURCE_AS_STRING", null, headers);

        assertNotNull(result, "resource result");
        LOG.debug("resource: {}", result);
        assertEquals(date, ((Patient) result.getResource()).getBirthDate(), "Birth date not updated!");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // test route for resource
                from("direct://RESOURCE")
                        .to("fhir://" + PATH_PREFIX + "/resource");

                // test route for resource
                from("direct://RESOURCE_WITH_STRING_ID")
                        .to("fhir://" + PATH_PREFIX + "/resource");

                // test route for resource
                from("direct://RESOURCE_AS_STRING")
                        .to("fhir://" + PATH_PREFIX + "/resource");

                // test route for resource
                from("direct://RESOURCE_AS_STRING_WITH_STRING_ID")
                        .to("fhir://" + PATH_PREFIX + "/resource");

                // test route for resourceBySearchUrl
                from("direct://RESOURCE_BY_SEARCH_URL")
                        .to("fhir://" + PATH_PREFIX + "/resourceBySearchUrl");

                // test route for resourceBySearchUrl
                from("direct://RESOURCE_BY_SEARCH_URL_AND_RESOURCE_AS_STRING")
                        .to("fhir://" + PATH_PREFIX + "/resourceBySearchUrl");

            }
        };
    }
}
