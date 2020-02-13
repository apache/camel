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

import java.security.InvalidKeyException;

import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.XMLSignature.SignatureValue;
import javax.xml.crypto.dsig.XMLSignatureException;

/**
 * Interrupts the validation by throwing an exception as soon as a validation
 * failure occurs and gives specific error messages.
 */
public class DefaultValidationFailedHandler implements ValidationFailedHandler {

    private StringBuilder error;

    @Override
    public Exception onXMLSignatureException(XMLSignatureException se) {
        if (se.getCause() instanceof InvalidKeyException) {
            return new XmlSignatureInvalidKeyException(se);
        } else {
            return new XmlSignatureException(se);
        }
    }

    @Override
    public void start() {
        error = new StringBuilder();
        error.append("Signature validation failed. ");
    }

    @Override
    public void signatureValueValidationFailed(SignatureValue value) throws Exception {
        error.append("The signature value could not be validated by the public key. Either the message has been tampered with or the public key is not correct.");
        throw new XmlSignatureInvalidValueException(error.toString());
    }

    @Override
    public void referenceValidationFailed(Reference ref) throws Exception {
        error.append(String
                .format("The calculated digest value of the document  %s is not equal to the value specified in the XML signature. The document may have been tampered with.",
                        getReferenceUriOrId(ref)));
        throw new XmlSignatureInvalidContentHashException(error.toString());
    }

    @Override
    public void manifestReferenceValidationFailed(Reference ref) throws Exception {
        error.append(String
                .format("The calculated digest value of the manifest  %s is not equal to the value specified in the XML signature. The document may have been tampered with.",
                        getReferenceUriOrId(ref)));
        throw new XmlSignatureInvalidContentHashException(error.toString());
    }

    @Override
    public void end() throws Exception {
        error = null;
    }

    private String getReferenceUriOrId(Reference ref) {
        String docId = ref.getURI();
        if (docId == null) {
            docId = ref.getId();
        }
        return docId;
    }

    @Override
    public boolean ignoreCoreValidationFailure() throws Exception {
        return false;
    }

}
