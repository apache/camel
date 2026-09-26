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
package org.apache.camel.builder.xml;

import java.io.Reader;
import java.util.Set;

import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;
import javax.xml.transform.stream.StreamSource;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.component.xslt.XsltUriResolver;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link XsltUriResolver} honours a factory's {@code ACCESS_EXTERNAL_STYLESHEET} restriction for the standard external
 * protocols. A denied external reference resolves to an empty document (so the XSLT {@code document()} function yields
 * an empty node-set rather than the resource content), while Camel-internal schemes (classpath, ref, bean) are outside
 * the JAXP external-access model and stay resolvable (CAMEL-24451).
 */
public class XsltUriResolverExternalAccessTest extends ContextTestSupport {

    @Test
    public void externalProtocolIsRefusedWhenAccessIsRestricted() throws Exception {
        // ACCESS_EXTERNAL_STYLESHEET="" -> no external protocol permitted; the file is never read, an empty document is
        // returned instead (a non-existent path so nothing could be read even if the guard were absent)
        XsltUriResolver resolver = new XsltUriResolver(context, null, XsltUriResolver.parseAllowedProtocols(""));
        Source source = resolver.resolve("file:/does-not-exist-camel-24451.xsl", null);
        assertTrue(readContent(source).contains("denied-external-access"),
                "expected an empty denied document, not the resolved resource");
    }

    @Test
    public void aProtocolOutsideTheAllowedListIsRefused() throws Exception {
        // only http permitted, so a file: reference is denied and resolves to the empty document
        XsltUriResolver resolver = new XsltUriResolver(context, null, XsltUriResolver.parseAllowedProtocols("http"));
        Source source = resolver.resolve("file:/does-not-exist-camel-24451.xsl", null);
        assertTrue(readContent(source).contains("denied-external-access"),
                "expected an empty denied document, not the resolved resource");
    }

    @Test
    public void anAllowedProtocolIsResolvedNormally() {
        // file permitted -> the guard does not intervene, so resolution is attempted and fails because the file is
        // absent (proving an allowed protocol is not short-circuited to the empty document)
        XsltUriResolver resolver = new XsltUriResolver(context, null, XsltUriResolver.parseAllowedProtocols("file"));
        assertThrows(TransformerException.class,
                () -> resolver.resolve("file:/does-not-exist-camel-24451.xsl", null));
    }

    @Test
    public void camelInternalSchemesAreNotGoverned() {
        // even with all external protocols denied, classpath: is a Camel-internal scheme, so the guard does not apply;
        // resolution is attempted and fails only because the resource is absent
        XsltUriResolver resolver = new XsltUriResolver(context, null, XsltUriResolver.parseAllowedProtocols(""));
        assertThrows(TransformerException.class,
                () -> resolver.resolve("classpath:does-not-exist-camel-24451.xsl", null));
    }

    @Test
    public void unrestrictedAccessDoesNotRefuseExternalProtocols() {
        // null (ACCESS_EXTERNAL_STYLESHEET unset or "all") -> no restriction, so resolution is attempted and fails
        // because the file is absent, rather than being short-circuited to the empty document
        XsltUriResolver resolver = new XsltUriResolver(context, null, null);
        assertThrows(TransformerException.class,
                () -> resolver.resolve("file:/does-not-exist-camel-24451.xsl", null));
    }

    @Test
    public void parseAllowedProtocols() {
        assertNull(XsltUriResolver.parseAllowedProtocols("all"));
        assertNull(XsltUriResolver.parseAllowedProtocols(null));
        assertEquals(Set.of(), XsltUriResolver.parseAllowedProtocols(""));
        assertEquals(Set.of("file", "http"), XsltUriResolver.parseAllowedProtocols("file, http"));
    }

    private static String readContent(Source source) throws Exception {
        StreamSource streamSource = assertInstanceOf(StreamSource.class, source);
        Reader reader = streamSource.getReader();
        StringBuilder sb = new StringBuilder();
        int c;
        while ((c = reader.read()) != -1) {
            sb.append((char) c);
        }
        return sb.toString();
    }
}
