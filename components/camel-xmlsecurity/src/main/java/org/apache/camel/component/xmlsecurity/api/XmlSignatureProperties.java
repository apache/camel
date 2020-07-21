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

import java.util.List;

import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.XMLObject;
import javax.xml.crypto.dsig.XMLSignatureFactory;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;

import org.w3c.dom.Node;

import org.apache.camel.Message;

/**
 * You can provide further XML objects and references which will be added by the
 * XML signature generator to the XML signature.
 */
public interface XmlSignatureProperties {

    /**
     * Returns further configuration objects for the XML signature
     * 
     * @param input
     *            input
     * @return output must not be <code>null</code>
     * @throws Exception
     *             if an error occurs during creating the output
     */
    Output get(Input input) throws Exception;

    public interface Input {

        /** Input message for reading header data */
        Message getMessage();

        /**
         * The message body as DOM node. If the message body is plain text then
         * the node will be a text node. If the message body is a XML document,
         * then the node is the root element.
         */
        Node getMessageBodyNode();

        /**
         * Returns the parent node of the signature element in the case of
         * enveloped or detached XML signature, or the empty result document in
         * the case of enveloping XML signature.
         * 
         * @return parent node, cannot be <code>null</code>
         */
        Node getParent();

        /** Key info. */
        KeyInfo getKeyInfo();

        /**
         * XML signature factory which can be used to create Reference and
         * XMLObject instances.
         * 
         * @return factory
         */
        XMLSignatureFactory getSignatureFactory();

        /**
         * Signature algorithm. Example:
         * "http://www.w3.org/2000/09/xmldsig#dsa-sha1".
         */
        String getSignatureAlgorithm();

        /**
         * Digest algorithm which is used for the digest calculation of the
         * message body.
         */
        String getContentDigestAlgorithm();

        /**
         * Signature Id. Can be <code>null</code>, then no signature Id
         * attribute is generated.
         */
        String getSignatureId();

        /**
         * Reference URI which points to the content of the original XML
         * document to be signed. For enveloped signature it is typically the
         * empty string. For detached signature, it is an ID attribute value
         * preceded by '#'. For enveloping signature, it is <code>null</code>.
         */
        String getContentReferenceUri();

        /**
         * Retruns the signature type.
         * 
         * @return signature type
         */
        SignatureType getSignatureType();

        /**
         * Returns the prefix for the XML Signature namespace
         * ("http://www.w3.org/2000/09/xmldsig#"). Can be null or empty.
         */
        String getPrefixForXmlSignatureNamespace();

    }

    class Output {

        private String contentReferenceId;

        private String signatureId;

        private List<? extends XMLObject> objects;

        private List<? extends Reference> references;

        public List<? extends XMLObject> getObjects() {
            return objects;
        }

        public void setObjects(List<? extends XMLObject> objects) {
            this.objects = objects;
        }

        public List<? extends Reference> getReferences() {
            return references;
        }

        public void setReferences(List<? extends Reference> references) {
            this.references = references;
        }

        public String getContentReferenceId() {
            return contentReferenceId;
        }

        /**
         * Id value for the reference of the signed content. Currently used by
         * the XAdES parameter DataObjectFormat. See XAdESSignatureProperties.
         * */
        public void setContentReferenceId(String contentReferenceId) {
            this.contentReferenceId = contentReferenceId;
        }

        public String getSignatureId() {
            return signatureId;
        }

        /**
         * You can overwrite the value of the Id attribute of the Signature
         * element that you get from {@link Input#getSignatureId()}. Only if the
         * provided value is not <code>null</code> and not empty, then the
         * signature Id will be overwritten.
         * 
         * @param signatureId
         *            Id attribute value of the Signature element
         */
        public void setSignatureId(String signatureId) {
            this.signatureId = signatureId;
        }

    }

}
