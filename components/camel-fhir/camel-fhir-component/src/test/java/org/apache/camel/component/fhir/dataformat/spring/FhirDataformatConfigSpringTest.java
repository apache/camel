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
package org.apache.camel.component.fhir.dataformat.spring;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import ca.uhn.fhir.context.FhirVersionEnum;
import ca.uhn.fhir.context.ParserOptions;
import ca.uhn.fhir.parser.LenientErrorHandler;
import org.apache.camel.CamelContext;
import org.apache.camel.component.fhir.FhirDataFormat;
import org.apache.camel.reifier.dataformat.DataFormatReifier;
import org.apache.camel.test.spring.junit5.CamelSpringTestSupport;
import org.hl7.fhir.r4.model.IdType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class FhirDataformatConfigSpringTest extends CamelSpringTestSupport {

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
    }

    @Test
    public void defaultFhirJsonConfigTest() {
        FhirDataFormat fhirJson = getDataformat("fhirJson");
        assertConfig(fhirJson);
    }

    @Test
    public void defaultFhirXmlConfigTest() {
        FhirDataFormat fhirXml = getDataformat("fhirXml");
        assertConfig(fhirXml);
    }

    private void assertConfig(FhirDataFormat fhirJson) {
        assertEquals(FhirVersionEnum.R4, fhirJson.getFhirContext().getVersion().getVersion());
        Set<String> dontEncodeElements = fhirJson.getDontEncodeElements();
        assertCollection(dontEncodeElements);
        List<String> dontStripVersionsFromReferencesAtPaths = fhirJson.getDontStripVersionsFromReferencesAtPaths();
        assertCollection(dontStripVersionsFromReferencesAtPaths);
        Set<String> encodeElements = fhirJson.getEncodeElements();
        assertCollection(encodeElements);
        assertTrue(fhirJson.getForceResourceId().getClass().isAssignableFrom(IdType.class));
        assertTrue(fhirJson.getParserErrorHandler().getClass().isAssignableFrom(LenientErrorHandler.class));
        assertTrue(fhirJson.getParserOptions().getClass().isAssignableFrom(ParserOptions.class));
        assertNotNull(fhirJson.getPreferTypesNames());
        assertEquals("serverBaseUrl", fhirJson.getServerBaseUrl());
        assertTrue(fhirJson.getOverrideResourceIdWithBundleEntryFullUrl());
        assertTrue(fhirJson.getStripVersionsFromReferences());
        assertTrue(fhirJson.isPrettyPrint());
        assertTrue(fhirJson.isEncodeElementsAppliesToChildResourcesOnly());
        assertTrue(fhirJson.isOmitResourceId());
        assertTrue(fhirJson.isSummaryMode());
        assertTrue(fhirJson.isSuppressNarratives());
    }

    private void assertCollection(Collection<String> encodeElements) {
        assertEquals(2, encodeElements.size());
        assertTrue(encodeElements.contains("foo"));
        assertTrue(encodeElements.contains("bar"));
    }

    private FhirDataFormat getDataformat(String name) {
        CamelContext camelContext = context();
        // TODO: Do not use reifier directly
        return (FhirDataFormat) DataFormatReifier.getDataFormat(camelContext, name);
    }

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/dataformat/fhir/FhirDataFormatConfigSpringTest.xml");
    }
}
