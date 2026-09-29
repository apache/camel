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

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.builder.xml.XMLConverterHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The always-installed {@link XsltUriResolver} now honours the transformer factory's {@code ACCESS_EXTERNAL_STYLESHEET}
 * restriction for the runtime {@code document()} function. Because Camel's default factory sets it to deny-all, an
 * external resource referenced through {@code document()} is refused by default (the transform fails with an access
 * error); relaxing the factory lets it through again (CAMEL-24451).
 */
public class XsltDocumentExternalAccessTest extends ContextTestSupport {

    private static final String XSL = "org/apache/camel/component/xslt/camel24451_document_external.xsl";
    // a file: stylesheet whose relative document() reference resolves to a file: URI beside it
    private static final String XSL_FILE_RELATIVE
            = "file:src/test/resources/org/apache/camel/component/xslt/camel24451_document_relative.xsl";
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
        // the default factory denies external access ("" == deny-all), so document() over an absolute file: URI (bound
        // from a message header) is refused: the transform fails instead of reading the (existing) file. The resolver's
        // TransformerException is turned into a document retrieval failure by the XSLT processor, so we assert the
        // transform fails rather than on the message text (the access reason is logged as a WARN)
        assertThrows(CamelExecutionException.class,
                () -> template.requestBodyAndHeader("direct:default", "<a/>", "externalUri", externalUri()));
    }

    @Test
    public void relativeDocumentInAFileStylesheetIsDeniedByDefault() {
        // a stylesheet loaded from file: whose relative document() resolves to a file: URI beside it is also refused by
        // default (the route author must relax the factory to allow it) - documented in the upgrade guide
        assertThrows(CamelExecutionException.class,
                () -> template.requestBody("direct:fileRelative", "<a/>"));
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
                from("direct:fileRelative").to("xslt:" + XSL_FILE_RELATIVE);
            }
        };
    }
}
