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
import org.apache.camel.component.fhir.internal.FhirReadApiMethod;
import org.hl7.fhir.dstu3.model.IdType;
import org.hl7.fhir.dstu3.model.Patient;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Test class for {@link org.apache.camel.component.fhir.api.FhirRead} APIs.
 * The class source won't be generated again if the generator MOJO finds it under src/test/java.
 */
public class FhirReadIT extends AbstractFhirTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(FhirReadIT.class);
    private static final String PATH_PREFIX = FhirApiCollection.getCollection().getApiName(FhirReadApiMethod.class).getName();

    @Test
    public void testResourceById() throws Exception {
        final Map<String, Object> headers = new HashMap<>();
        // parameter type is Class
        headers.put("CamelFhir.resource", Patient.class);
        // parameter type is org.hl7.fhir.instance.model.api.IIdType
        headers.put("CamelFhir.id", patient.getIdElement());

        Patient result = requestBodyAndHeaders("direct://RESOURCE_BY_ID", null, headers);

        assertValidResponse(result);
    }

    @Test
    public void testResourceByLongId() throws Exception {
        final Map<String, Object> headers = new HashMap<>();
        // parameter type is Class
        headers.put("CamelFhir.resource", Patient.class);
        // parameter type is Long
        headers.put("CamelFhir.longId", Long.valueOf(patient.getIdElement().getIdPart()));

        Patient result = requestBodyAndHeaders("direct://RESOURCE_BY_LONG_ID", null, headers);

        assertValidResponse(result);
    }

    @Test
    public void testResourceByStringId() throws Exception {
        final Map<String, Object> headers = new HashMap<>();
        // parameter type is Class
        headers.put("CamelFhir.resource", Patient.class);
        // parameter type is Long
        headers.put("CamelFhir.stringId", patient.getIdElement().getIdPart());

        Patient result = requestBodyAndHeaders("direct://RESOURCE_BY_STRING_ID", null, headers);

        assertValidResponse(result);
    }

    @Test
    public void testResourceByIdAndStringResource() throws Exception {
        final Map<String, Object> headers = new HashMap<>();
        // parameter type is Class
        headers.put("CamelFhir.resourceClass", "Patient");
        // parameter type is org.hl7.fhir.instance.model.api.IIdType
        headers.put("CamelFhir.id", patient.getIdElement());

        Patient result = requestBodyAndHeaders("direct://RESOURCE_BY_ID_AND_STRING_RESOURCE", null, headers);

        assertValidResponse(result);
    }

    @Test
    public void testResourceByLongIdAndStringResource() throws Exception {
        final Map<String, Object> headers = new HashMap<>();
        // parameter type is Class
        headers.put("CamelFhir.resource", Patient.class);
        // parameter type is Long
        headers.put("CamelFhir.longId", Long.valueOf(patient.getIdElement().getIdPart()));

        Patient result = requestBodyAndHeaders("direct://RESOURCE_BY_LONG_ID_AND_STRING_RESOURCE", null, headers);

        assertValidResponse(result);
    }

    @Test
    public void testResourceByStringIdAndStringResource() throws Exception {
        final Map<String, Object> headers = new HashMap<>();
        // parameter type is Class
        headers.put("CamelFhir.resource", Patient.class);
        // parameter type is Long
        headers.put("CamelFhir.stringId", patient.getIdElement().getIdPart());

        Patient result = requestBodyAndHeaders("direct://RESOURCE_BY_STRING_ID_AND_STRING_RESOURCE", null, headers);

        assertValidResponse(result);
    }

    @Test
    public void testResourceByStringIdAndVersion() throws Exception {
        final Map<String, Object> headers = new HashMap<>();
        // parameter type is Class
        headers.put("CamelFhir.resource", Patient.class);
        // parameter type is Long
        headers.put("CamelFhir.stringId", patient.getIdElement().getIdPart());
        // parameter type is String
        headers.put("CamelFhir.version", patient.getIdElement().getVersionIdPart());

        Patient result = requestBodyAndHeaders("direct://RESOURCE_BY_STRING_ID_AND_VERSION", null, headers);

        assertValidResponse(result);
    }

    @Test
    public void testResourceByStringIdAndVersionWithResourceClass() throws Exception {
        final Map<String, Object> headers = new HashMap<>();
        // parameter type is Class
        headers.put("CamelFhir.resourceClass", "Patient");
        // parameter type is Long
        headers.put("CamelFhir.stringId", patient.getIdElement().getIdPart());
        // parameter type is String
        headers.put("CamelFhir.version", patient.getIdElement().getVersionIdPart());

        Patient result = requestBodyAndHeaders("direct://RESOURCE_BY_STRING_ID_AND_VERSION_AND_STRING_RESOURCE", null, headers);

        assertValidResponse(result);
    }

    @Test
    public void testResourceByiUrl() throws Exception {
        final Map<String, Object> headers = new HashMap<>();
        // parameter type is Class
        headers.put("CamelFhir.resource", Patient.class);
        // parameter type is org.hl7.fhir.instance.model.api.IIdType
        headers.put("CamelFhir.iUrl", new IdType(this.patient.getId()));

        Patient result = requestBodyAndHeaders("direct://RESOURCE_BY_IURL", null, headers);

        assertValidResponse(result);
    }

    @Test
    public void testResourceByUrl() throws Exception {
        final Map<String, Object> headers = new HashMap<>();
        // parameter type is Class
        headers.put("CamelFhir.resource", Patient.class);
        // parameter type is String
        headers.put("CamelFhir.url", this.patient.getId());

        Patient result = requestBodyAndHeaders("direct://RESOURCE_BY_URL", null, headers);

        assertValidResponse(result);
    }

    @Test
    public void testResourceByStringUrlAndStringResource() throws Exception {
        final Map<String, Object> headers = new HashMap<>();
        // parameter type is String
        headers.put("CamelFhir.resourceClass", "Patient");
        // parameter type is org.hl7.fhir.instance.model.api.IIdType
        headers.put("CamelFhir.iUrl", new IdType(this.patient.getId()));

        Patient result = requestBodyAndHeaders("direct://RESOURCE_BY_STRING_URL_AND_STRING_RESOURCE", null, headers);

        assertValidResponse(result);
    }

    @Test
    public void testResourceByUrlAndStringResource() throws Exception {
        final Map<String, Object> headers = new HashMap<>();
        // parameter type is String
        headers.put("CamelFhir.resourceClass", "Patient");
        // parameter type is String
        headers.put("CamelFhir.url", this.patient.getId());

        Patient result = requestBodyAndHeaders("direct://RESOURCE_BY_URL_AND_STRING_RESOURCE", null, headers);

        assertValidResponse(result);
    }

    @Test
    public void testResourceByUrlAndStringResourcePrettyPrint() throws Exception {
        final Map<String, Object> headers = new HashMap<>();
        // parameter type is String
        headers.put("CamelFhir.resourceClass", "Patient");
        // parameter type is String
        headers.put("CamelFhir.url", this.patient.getId());
        headers.put(ExtraParameters.PRETTY_PRINT.getHeaderName(), Boolean.TRUE);

        Patient result = requestBodyAndHeaders("direct://RESOURCE_BY_URL_AND_STRING_RESOURCE", null, headers);

        assertValidResponse(result);
    }

    private void assertValidResponse(Patient result) {
        LOG.debug("response: " + result);
        assertNotNull("resourceByUrl result", result);
        assertEquals("Freeman", result.getName().get(0).getFamily());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() {
                // test route for resourceById
                from("direct://RESOURCE_BY_ID")
                    .to("fhir://" + PATH_PREFIX + "/resourceById");

                // test route for resourceById
                from("direct://RESOURCE_BY_LONG_ID")
                    .to("fhir://" + PATH_PREFIX + "/resourceById");

                // test route for resourceById
                from("direct://RESOURCE_BY_STRING_ID")
                    .to("fhir://" + PATH_PREFIX + "/resourceById");

                // test route for resourceById
                from("direct://RESOURCE_BY_ID_AND_STRING_RESOURCE")
                    .to("fhir://" + PATH_PREFIX + "/resourceById");

                // test route for resourceById
                from("direct://RESOURCE_BY_LONG_ID_AND_STRING_RESOURCE")
                    .to("fhir://" + PATH_PREFIX + "/resourceById");

                // test route for resourceById
                from("direct://RESOURCE_BY_STRING_ID_AND_STRING_RESOURCE")
                    .to("fhir://" + PATH_PREFIX + "/resourceById");

                // test route for resourceById
                from("direct://RESOURCE_BY_STRING_ID_AND_VERSION")
                    .to("fhir://" + PATH_PREFIX + "/resourceById");

                // test route for resourceById
                from("direct://RESOURCE_BY_STRING_ID_AND_VERSION_AND_STRING_RESOURCE")
                    .to("fhir://" + PATH_PREFIX + "/resourceById");

                // test route for resourceByUrl
                from("direct://RESOURCE_BY_IURL")
                    .to("fhir://" + PATH_PREFIX + "/resourceByUrl");

                // test route for resourceByUrl
                from("direct://RESOURCE_BY_URL")
                    .to("fhir://" + PATH_PREFIX + "/resourceByUrl");

                // test route for resourceByUrl
                from("direct://RESOURCE_BY_STRING_URL_AND_STRING_RESOURCE")
                    .to("fhir://" + PATH_PREFIX + "/resourceByUrl");

                // test route for resourceByUrl
                from("direct://RESOURCE_BY_URL_AND_STRING_RESOURCE")
                    .to("fhir://" + PATH_PREFIX + "/resourceByUrl");

            }
        };
    }
}
