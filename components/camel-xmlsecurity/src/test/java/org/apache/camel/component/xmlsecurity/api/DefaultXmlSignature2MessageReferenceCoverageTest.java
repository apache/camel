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
package org.apache.camel.component.xmlsecurity.api;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;

import javax.xml.crypto.Data;
import javax.xml.crypto.dsig.DigestMethod;
import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.XMLObject;
import javax.xml.crypto.dsig.XMLSignatureException;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import org.apache.camel.component.xmlsecurity.api.XmlSignature2Message.Input;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The default output node search emits the whole document element for an enveloped or detached signature. Validation
 * only proves that each Reference's digest matches what that Reference resolves to, so a signed fragment embedded in a
 * larger document still validates while the surrounding, unsigned content goes downstream as verified content.
 * <p>
 * {@code enforceReferenceCoverage} closes that for routes where the signature is expected to cover the document
 * element. It cannot be the default: the component's detached-signature flow deliberately covers a sub-element and
 * emits the whole document, and nothing in the document distinguishes the two.
 */
class DefaultXmlSignature2MessageReferenceCoverageTest {

    private static final String WRAPPED = "<attacker><signed ID=\"myID\"><b>bValue</b></signed></attacker>";
    private static final String PLAIN = "<signed ID=\"myID\"><b>bValue</b></signed>";

    @Test
    void offByDefault() {
        assertFalse(new DefaultXmlSignature2Message().isEnforceReferenceCoverage());
    }

    @Test
    void aFragmentReferenceDoesNotCoverTheDocumentElement() {
        XmlSignatureException e = assertThrows(XmlSignatureException.class, () -> check(WRAPPED, "#myID"));
        assertTrue(e.getMessage().contains("None of the validated References covers the document element"),
                "unexpected message: " + e.getMessage());
    }

    @Test
    void anEmptyReferenceUriCoversTheWholeDocument() throws Exception {
        check(WRAPPED, "");
    }

    @Test
    void aReferenceToTheDocumentElementsOwnIdIsAccepted() throws Exception {
        check(PLAIN, "#myID");
    }

    @Test
    void anXPointerToTheDocumentElementsOwnIdIsAccepted() throws Exception {
        check(PLAIN, "#xpointer(id('myID'))");
    }

    @Test
    void aWholeDocumentXPointerIsAccepted() throws Exception {
        check(WRAPPED, "#xpointer(/)");
    }

    @Test
    void anExternalReferenceIsLeftAlone() throws Exception {
        // The signature says nothing about this document either way, so there is nothing to correlate
        check(WRAPPED, "http://example.org/other.xml");
    }

    private static void check(String xml, String referenceUri) throws Exception {
        DefaultXmlSignature2Message mapper = new DefaultXmlSignature2Message();
        mapper.setEnforceReferenceCoverage(true);

        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        Element documentElement = document.getDocumentElement();

        mapper.checkDocumentElementIsCoveredByAReference(new TestInput(referenceUri), documentElement);
    }

    /**
     * Only getReferences() is consulted by the check under test.
     */
    private static final class TestInput implements Input {

        private final String referenceUri;

        private TestInput(String referenceUri) {
            this.referenceUri = referenceUri;
        }

        @Override
        public List<Reference> getReferences() {
            return Collections.singletonList(new TestReference(referenceUri));
        }

        @Override
        public List<XMLObject> getObjects() {
            return Collections.emptyList();
        }

        @Override
        public Document getMessageBodyDocument() {
            return null;
        }

        @Override
        public Object getOutputNodeSearch() {
            return null;
        }

        @Override
        public String getOutputNodeSearchType() {
            return DefaultXmlSignature2Message.OUTPUT_NODE_SEARCH_TYPE_DEFAULT;
        }

        @Override
        public Boolean getRemoveSignatureElements() {
            return Boolean.FALSE;
        }

        @Override
        public Boolean omitXmlDeclaration() {
            return Boolean.FALSE;
        }

        @Override
        public String getOutputXmlEncoding() {
            return null;
        }
    }

    private static final class TestReference implements Reference {

        private final String uri;

        private TestReference(String uri) {
            this.uri = uri;
        }

        @Override
        public String getURI() {
            return uri;
        }

        @Override
        public String getType() {
            return null;
        }

        @Override
        public String getId() {
            return null;
        }

        @Override
        public byte[] getDigestValue() {
            return new byte[0];
        }

        @Override
        public byte[] getCalculatedDigestValue() {
            return new byte[0];
        }

        @Override
        public boolean validate(javax.xml.crypto.dsig.XMLValidateContext validateContext) throws XMLSignatureException {
            return true;
        }

        @Override
        public DigestMethod getDigestMethod() {
            return null;
        }

        @Override
        @SuppressWarnings("rawtypes")
        public List getTransforms() {
            return Collections.emptyList();
        }

        @Override
        public Data getDereferencedData() {
            return null;
        }

        @Override
        public InputStream getDigestInputStream() {
            return null;
        }

        @Override
        public boolean isFeatureSupported(String feature) {
            return false;
        }
    }
}
