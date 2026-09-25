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
package org.apache.camel.component.xslt;

import java.io.File;

import javax.xml.XMLConstants;
import javax.xml.transform.TransformerFactory;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.builder.xml.XMLConverterHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The always-installed {@link XsltUriResolver} now honours the transformer factory's {@code ACCESS_EXTERNAL_STYLESHEET}
 * restriction for the runtime {@code document()} function. Because Camel's default factory sets it to deny-all, an
 * external resource referenced through {@code document()} - here via a URI arriving as a (untrusted) message header
 * bound as a stylesheet parameter - is not read by default; relaxing the factory lets it through again (CAMEL-24451).
 */
public class XsltDocumentExternalAccessTest extends ContextTestSupport {

    private static final String XSL = "org/apache/camel/component/xslt/camel24451_document_external.xsl";
    private static final String MARKER = "EXTERNAL-DATA-CAMEL-24451";

    // an absolute file: URI to an existing resource - what an attacker-controlled header could point document() at
    private static String externalUri() {
        return new File("src/test/resources/org/apache/camel/component/xslt/camel24451_external_lookup.xml")
                .getAbsoluteFile().toURI().toString();
    }

    /**
     * Registers a factory that keeps Camel's hardening but permits the {@code file} protocol for external
     * stylesheet/document access, mirroring what an operator would configure to opt back in.
     */
    @Override
    protected Registry createCamelRegistry() throws Exception {
        Registry registry = super.createCamelRegistry();
        TransformerFactory factory = new XMLConverterHelper().createTransformerFactory();
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "file");
        registry.bind("relaxedFactory", factory);
        return registry;
    }

    @Test
    public void externalDocumentIsDeniedByDefault() {
        // the default factory denies external access ("" == deny-all), so document() must not read the file
        String body = template.requestBodyAndHeader("direct:default", "<a/>", "externalUri", externalUri(), String.class);
        assertFalse(body.contains(MARKER),
                "external document must not be read under the default deny-all policy, got: " + body);
    }

    @Test
    public void externalDocumentIsAllowedWhenTheFactoryPermitsFile() {
        // a factory whose ACCESS_EXTERNAL_STYLESHEET permits file lets document() read the external resource again
        String body = template.requestBodyAndHeader("direct:relaxed", "<a/>", "externalUri", externalUri(), String.class);
        assertTrue(body.contains(MARKER), "expected the external document to be read, got: " + body);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:default").to("xslt:" + XSL);
                from("direct:relaxed").to("xslt:" + XSL + "?transformerFactory=#relaxedFactory");
            }
        };
    }
}
