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

import org.w3c.dom.Document;

import org.apache.camel.Message;

/**
 * Used in the signature verifier to map the references and objects of the XML
 * signature to the output message.
 */
public interface XmlSignature2Message {

    /**
     * Maps the references and objects of an XML signature to the camel message.
     * 
     * @param input
     *            input
     * @param output
     *            output message
     * @throws Exception
     */
    void mapToMessage(Input input, Message output) throws Exception;

    public interface Input {

        /**
         * Returns the references. If there were several signatures then all
         * references of all signatures are returned.
         * 
         * @return list of references, cannot be <code>null</code>
         */
        List<Reference> getReferences();

        /**
         * Returns the objects. If there were several signatures in the XML
         * document then all objects of all signatures are returned.
         * 
         * @return objects, cannot be <code>null</code>
         */
        List<XMLObject> getObjects();

        /** Message body containing the XML signature as DOM. */
        Document getMessageBodyDocument();

        /**
         * Indicator whether XML declaration should be omitted. Configured in
         * the endpoint URI.
         * 
         * @return {@link Boolean#TRUE} if the XML declaration shall be omitted
         *         in the output document.
         */
        Boolean omitXmlDeclaration();

        /**
         * Output node search value for determining the node from the XML
         * signature document which shall be set to the output message body.
         */
        Object getOutputNodeSearch();

        /**
         * Search type. Which determines the class and meaning of
         * {@link #getOutputNodeSearch()}.
         */
        String getOutputNodeSearchType();

        /**
         * Indicator whether the XML signature elements should be removed from
         * the document set to the output message.
         */
        Boolean getRemoveSignatureElements();

        /**
         * The character encoding of the resulting XML document. Can be
         * <code>null</code>. If <code>null</code> then the encoding of the
         * original XML document is used.
         * 
         */
        String getOutputXmlEncoding();

    }

}
