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

import java.util.Set;

import javax.xml.transform.TransformerException;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.component.xslt.XsltUriResolver;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link XsltUriResolver} honours a factory's {@code ACCESS_EXTERNAL_STYLESHEET} restriction for the standard external
 * protocols by throwing a {@link TransformerException} (matching plain JAXP, which reports an access error).
 * Camel-internal schemes (classpath, ref, bean) are outside the JAXP external-access model and stay resolvable
 * (CAMEL-24451).
 */
public class XsltUriResolverExternalAccessTest extends ContextTestSupport {

    @Test
    public void externalProtocolIsRefusedWhenAccessIsRestricted() {
        // ACCESS_EXTERNAL_STYLESHEET="" -> no external protocol permitted (a non-existent file so nothing is read)
        XsltUriResolver resolver = new XsltUriResolver(context, null, XsltUriResolver.parseAllowedProtocols(""));
        TransformerException e = assertThrows(TransformerException.class,
                () -> resolver.resolve("file:/does-not-exist-camel-24451.xsl", null));
        assertTrue(e.getMessage().contains("ACCESS_EXTERNAL_STYLESHEET"), "unexpected message: " + e.getMessage());
    }

    @Test
    public void aProtocolOutsideTheAllowedListIsRefused() {
        // only http permitted, so a file: reference is refused with the access-denied message
        XsltUriResolver resolver = new XsltUriResolver(context, null, XsltUriResolver.parseAllowedProtocols("http"));
        TransformerException e = assertThrows(TransformerException.class,
                () -> resolver.resolve("file:/does-not-exist-camel-24451.xsl", null));
        assertTrue(e.getMessage().contains("ACCESS_EXTERNAL_STYLESHEET"), "unexpected message: " + e.getMessage());
    }

    @Test
    public void anAllowedProtocolIsResolvedNormally() {
        // file permitted -> the guard does not intervene, so resolution is attempted and fails only because the file is
        // absent (the failure is not the access-denied message)
        XsltUriResolver resolver = new XsltUriResolver(context, null, XsltUriResolver.parseAllowedProtocols("file"));
        TransformerException e = assertThrows(TransformerException.class,
                () -> resolver.resolve("file:/does-not-exist-camel-24451.xsl", null));
        assertTrue(!e.getMessage().contains("ACCESS_EXTERNAL_STYLESHEET"), "unexpected access denial: " + e.getMessage());
    }

    @Test
    public void camelInternalSchemesAreNotGoverned() {
        // even with all external protocols denied, classpath: is a Camel-internal scheme, so the access check does not
        // apply; resolution proceeds and fails only because the resource is absent
        XsltUriResolver resolver = new XsltUriResolver(context, null, XsltUriResolver.parseAllowedProtocols(""));
        TransformerException e = assertThrows(TransformerException.class,
                () -> resolver.resolve("classpath:does-not-exist-camel-24451.xsl", null));
        assertTrue(!e.getMessage().contains("ACCESS_EXTERNAL_STYLESHEET"),
                "classpath must not be access-governed: " + e.getMessage());
    }

    @Test
    public void unrestrictedAccessDoesNotRefuseExternalProtocols() {
        // null (ACCESS_EXTERNAL_STYLESHEET unset or "all") -> no access restriction, so a failure is resolution, not an
        // access denial
        XsltUriResolver resolver = new XsltUriResolver(context, null, null);
        TransformerException e = assertThrows(TransformerException.class,
                () -> resolver.resolve("file:/does-not-exist-camel-24451.xsl", null));
        assertTrue(!e.getMessage().contains("ACCESS_EXTERNAL_STYLESHEET"), "unexpected access denial: " + e.getMessage());
    }

    @Test
    public void parseAllowedProtocols() {
        assertNull(XsltUriResolver.parseAllowedProtocols("all"));
        assertNull(XsltUriResolver.parseAllowedProtocols(null));
        assertEquals(Set.of(), XsltUriResolver.parseAllowedProtocols(""));
        assertEquals(Set.of("file", "http"), XsltUriResolver.parseAllowedProtocols("file, http"));
    }
}
