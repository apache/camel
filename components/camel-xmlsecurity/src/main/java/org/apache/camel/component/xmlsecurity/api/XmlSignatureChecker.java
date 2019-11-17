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

import javax.xml.crypto.dsig.SignedInfo;
import javax.xml.crypto.dsig.XMLObject;
import javax.xml.crypto.dsig.XMLSignature.SignatureValue;
import javax.xml.crypto.dsig.keyinfo.KeyInfo;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import org.apache.camel.Message;

/**
 * This interface gives the application the possibility to check whether the
 * expected parts are signed.
 * <p>
 * Only relevant for the XML signature verifier.
 * <p>
 * See http://www.w3.org/TR/xmldsig-bestpractices/#check-what-is-signed
 * <p>
 * The checker is called for each XML signature contained in the XM document.
 * You can get the total and current count of the XML signatures via the methods
 * {@link Input#getTotalCountOfSignatures()} and
 * {@link Input#getCurrentCountOfSignatures()}.
 */
public interface XmlSignatureChecker {

    /**
     * Checks whether the signature document has the expected structure and
     * contains the expected transformations and references. See
     * http://www.w3.org/TR/xmldsig-bestpractices/#check-what-is-signed
     * 
     * @param input
     *            input parameters
     * @throws Exception
     *             when XML signature does not pass the check
     */
    void checkBeforeCoreValidation(Input input) throws Exception;

    public interface Input {

        /** Signed info instance. */
        SignedInfo getSignedInfo();

        /** Signature value instance. */
        SignatureValue getSignatureValue();

        /** XML objects list. */
        List<? extends XMLObject> getObjects();

        /** Key info. */
        KeyInfo getKeyInfo();

        /** Message body containing the XML signature as DOM. */
        Document getMessageBodyDocument();

        /** Message. */
        Message getMessage();

        /**
         * Returns true if a XML schema validation was executed during the
         * parsing of the XML document.
         * 
         */
        boolean isXmlSchemaValidationExecuted();

        /**
         * Returns the total count of XML signatures contained in the document.
         * 
         */
        int getTotalCountOfSignatures();

        /**
         * Returns the current count of XML signatures starting from 1.
         * 
         */
        int getCurrentCountOfSignatures();

        /** Current signature element. */
        Element getCurrentSignatureElement();

    }

}
