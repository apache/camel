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
import org.apache.camel.component.fhir.internal.FhirMetaApiMethod;
import org.hl7.fhir.instance.model.api.IBaseMetaType;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Patient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Test class for {@link org.apache.camel.component.fhir.api.FhirMeta} APIs. The class source won't be generated again
 * if the generator MOJO finds it under src/test/java.
 */
@DisabledIfSystemProperty(named = "ci.env.name", matches = "apache.org",
                          disabledReason = "Apache CI nodes are too resource constrained for this test - see CAMEL-19659")
public class FhirMetaIT extends AbstractFhirTestSupport {

    private static final Logger LOG = LoggerFactory.getLogger(FhirMetaIT.class);
    private static final String PATH_PREFIX = FhirApiCollection.getCollection().getApiName(FhirMetaApiMethod.class).getName();

    @Test
    public void testAdd() {
        //assert no meta
        Meta meta = fhirClient.meta().get(Meta.class).fromResource(this.patient.getIdElement()).execute();
        assertEquals(0, meta.getTag().size());
        Meta inMeta = new Meta();
        inMeta.addTag().setSystem("urn:system1").setCode("urn:code1");
        final Map<String, Object> headers = new HashMap<>();
        // parameter type is org.hl7.fhir.instance.model.api.IBaseMetaType
        headers.put("CamelFhir.meta", inMeta);
        // parameter type is org.hl7.fhir.instance.model.api.IIdType
        headers.put("CamelFhir.id", this.patient.getIdElement());

        IBaseMetaType result = requestBodyAndHeaders("direct://ADD", null, headers);

        LOG.debug("add: {}", result);
        assertNotNull(result, "add result");
        assertEquals(1, result.getTag().size());
    }

    @Test
    public void testDelete() {
        //assert no meta
        Meta meta = fhirClient.meta().get(Meta.class).fromResource(this.patient.getIdElement()).execute();
        assertEquals(0, meta.getTag().size());
        Meta inMeta = new Meta();
        inMeta.addTag().setSystem("urn:system1").setCode("urn:code1");
        // add meta
        meta = fhirClient.meta().add().onResource(this.patient.getIdElement()).meta(inMeta).execute();
        assertEquals(1, meta.getTag().size());

        //delete meta
        final Map<String, Object> headers = new HashMap<>();
        // parameter type is org.hl7.fhir.instance.model.api.IBaseMetaType
        headers.put("CamelFhir.meta", meta);
        // parameter type is org.hl7.fhir.instance.model.api.IIdType
        headers.put("CamelFhir.id", this.patient.getIdElement());

        IBaseMetaType result = requestBodyAndHeaders("direct://DELETE", null, headers);

        LOG.debug("delete: {}", result);
        assertNotNull(result, "delete result");
        assertEquals(0, result.getTag().size());
    }

    @Test
    public void testGetFromResource() {
        final Map<String, Object> headers = new HashMap<>();
        // parameter type is Class
        headers.put("CamelFhir.metaType", Meta.class);
        // parameter type is org.hl7.fhir.instance.model.api.IIdType
        headers.put("CamelFhir.id", this.patient.getIdElement());

        IBaseMetaType result = requestBodyAndHeaders("direct://GET_FROM_RESOURCE", null, headers);

        LOG.debug("getFromResource: {}", result);
        assertNotNull(result, "getFromResource result");
        assertEquals(0, result.getTag().size());
    }

    @Test
    public void testGetFromServer() {
        // using Class message body for single parameter "metaType"
        IBaseMetaType result = requestBody("direct://GET_FROM_SERVER", Meta.class);
        assertNotNull(result, "getFromServer result");
        LOG.debug("getFromServer: {}", result);
    }

    @Test
    public void testGetFromType() {
        final Map<String, Object> headers = new HashMap<>();
        // parameter type is Class
        headers.put("CamelFhir.metaType", Meta.class);
        // parameter type is String
        headers.put("CamelFhir.resourceType", "Patient");

        IBaseMetaType result = requestBodyAndHeaders("direct://GET_FROM_TYPE", null, headers);

        LOG.debug("getFromType: {}", result);
        assertNotNull(result, "getFromType result");
    }

    @Test
    public void testGetFromTypePreferResponseType() {
        final Map<String, Object> headers = new HashMap<>();
        // parameter type is Class
        headers.put("CamelFhir.metaType", Meta.class);
        // parameter type is String
        headers.put("CamelFhir.resourceType", "Patient");
        headers.put(ExtraParameters.PREFER_RESPONSE_TYPE.getHeaderName(), Patient.class);

        Meta result = requestBodyAndHeaders("direct://GET_FROM_TYPE", null, headers);

        LOG.debug("getFromType: {}", result);
        assertNotNull(result, "getFromType result");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                // test route for add
                from("direct://ADD")
                        .to("fhir://" + PATH_PREFIX + "/add");

                // test route for delete
                from("direct://DELETE")
                        .to("fhir://" + PATH_PREFIX + "/delete");

                // test route for getFromResource
                from("direct://GET_FROM_RESOURCE")
                        .to("fhir://" + PATH_PREFIX + "/getFromResource");

                // test route for getFromServer
                from("direct://GET_FROM_SERVER")
                        .to("fhir://" + PATH_PREFIX + "/getFromServer?inBody=metaType");

                // test route for getFromType
                from("direct://GET_FROM_TYPE")
                        .to("fhir://" + PATH_PREFIX + "/getFromType");

            }
        };
    }
}
