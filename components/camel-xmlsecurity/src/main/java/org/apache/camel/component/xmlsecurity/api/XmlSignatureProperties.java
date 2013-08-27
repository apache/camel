/**
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
         * enveloped XML signature. <code>null</code> is returned in the case of
         * enveloping XML signature.
         * 
         * @return parent node or <code>null</code>
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

        /** Signature Id. Can be <code>null</code>. */
        String getSignatureId();

    }

    public static class Output {

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

    }

}
