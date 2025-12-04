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

package org.apache.camel.component.docling;

import static org.junit.jupiter.api.Assertions.*;

import org.apache.camel.Endpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

public class DoclingComponentTest extends CamelTestSupport {

    @Test
    public void testCreateEndpoint() throws Exception {
        Endpoint endpoint = context.getEndpoint("docling:convert");
        assertNotNull(endpoint);
        assertTrue(endpoint instanceof DoclingEndpoint);

        DoclingEndpoint doclingEndpoint = (DoclingEndpoint) endpoint;
        assertEquals("convert", doclingEndpoint.getOperationId());
        assertEquals(
                DoclingOperations.CONVERT_TO_MARKDOWN,
                doclingEndpoint.getConfiguration().getOperation());
        assertTrue(doclingEndpoint.getConfiguration().isEnableOCR()); // OCR is enabled by default
    }

    @Test
    public void testCreateEndpointWithParameters() throws Exception {
        Endpoint endpoint =
                context.getEndpoint("docling:process?operation=CONVERT_TO_HTML&enableOCR=false&ocrLanguage=es");
        assertNotNull(endpoint);
        assertTrue(endpoint instanceof DoclingEndpoint);

        DoclingEndpoint doclingEndpoint = (DoclingEndpoint) endpoint;
        assertEquals("process", doclingEndpoint.getOperationId());
        assertEquals(
                DoclingOperations.CONVERT_TO_HTML,
                doclingEndpoint.getConfiguration().getOperation());
        assertFalse(doclingEndpoint.getConfiguration().isEnableOCR());
        assertEquals("es", doclingEndpoint.getConfiguration().getOcrLanguage());
    }

    @Test
    public void testProducerCreation() throws Exception {
        DoclingEndpoint endpoint = (DoclingEndpoint) context.getEndpoint("docling:convert");
        assertNotNull(endpoint.createProducer());
    }
}
