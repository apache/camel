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

import ca.uhn.fhir.context.FhirVersionEnum;
import org.apache.camel.CamelContext;
import org.apache.camel.component.fhir.FhirDataFormat;
import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.junit.Before;
import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;

public class FhirDataformatDefaultConfigSpringTest extends CamelSpringTestSupport {

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    @Test
    public void defaultFhirJsonConfigTest() {
        FhirDataFormat fhirJson = getDataformat("fhirJson");
        assertDefaultConfig(fhirJson);
    }

    @Test
    public void defaultFhirXmlConfigTest() {
        FhirDataFormat fhirXml = getDataformat("fhirXml");
        assertDefaultConfig(fhirXml);
    }

    private void assertDefaultConfig(FhirDataFormat fhirJson) {
        assertEquals(FhirVersionEnum.DSTU3, fhirJson.getFhirContext().getVersion().getVersion());
        assertNull(fhirJson.getDontEncodeElements());
        assertNull(fhirJson.getDontStripVersionsFromReferencesAtPaths());
        assertNull(fhirJson.getEncodeElements());
        assertNull(fhirJson.getForceResourceId());
        assertNull(fhirJson.getParserErrorHandler());
        assertNull(fhirJson.getParserOptions());
        assertNull(fhirJson.getPreferTypes());
        assertNull(fhirJson.getServerBaseUrl());
        assertNull(fhirJson.getOverrideResourceIdWithBundleEntryFullUrl());
        assertNull(fhirJson.getStripVersionsFromReferences());
        assertFalse(fhirJson.isPrettyPrint());
        assertFalse(fhirJson.isEncodeElementsAppliesToChildResourcesOnly());
        assertFalse(fhirJson.isOmitResourceId());
        assertFalse(fhirJson.isSummaryMode());
        assertFalse(fhirJson.isSuppressNarratives());
    }

    private FhirDataFormat getDataformat(String name) {
        CamelContext camelContext = context();
        return camelContext.getRegistry().lookupByNameAndType(name, FhirDataFormat.class);
    }

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/dataformat/fhir/FhirDataFormatDefaultConfigSpringTest.xml");
    }
}
