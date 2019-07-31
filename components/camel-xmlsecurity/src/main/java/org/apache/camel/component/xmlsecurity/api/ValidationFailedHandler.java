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

import javax.xml.crypto.dsig.Reference;
import javax.xml.crypto.dsig.XMLSignature;
import javax.xml.crypto.dsig.XMLSignature.SignatureValue;
import javax.xml.crypto.dsig.XMLSignatureException;

/**
 * Handler for handling the case when the core signature validation fails or a
 * {@link XMLSignatureException} occurs during calling
 * {@link XMLSignature#validate(javax.xml.crypto.dsig.XMLValidateContext)}.
 * 
 * This handler can be used to react in a specific way on validation failures.
 * For example, the handler could write logs or may even ignore certain
 * validation failures.
 * <p>
 * Typically the handler throws an exception when a validation failure occurs.
 * 
 * There is a certain order how the methods are called.
 * <ul>
 * <li>First, the method {@link #start()} is called when a core validation
 * fails.
 * <li>Then {@link #signatureValueValidationFailed(SignatureValue)} is called if
 * the signature validation fails.
 * <li>Then, for each reference in the signed info whose validation fails
 * {@link #referenceValidationFailed(Reference)} is called.
 * <li>Then, for each reference in the manifests whose validation fails, the
 * method {@link #manifestReferenceValidationFailed(Reference)} is called.
 * <li>Then, the method {@link #ignoreCoreValidationFailure()} is called where
 * you can finally decide whether the processing should go on or be interrupted.
 * <li>It is ensured that the method {@link #end()} is called at the end of the
 * validation, even if the methods called before have thrown an exception. This
 * allows you to hold state between the start and end of the validation handling
 * process.
 * </ul>
 * If you throw an exception then the validation checking is interrupted and
 * after that only the {@link #end()} method is called in a finally block. Best
 * practice is to interrupt the validation at the first occurrence of a
 * validation error.
 */
public interface ValidationFailedHandler {

    /**
     * Method called when an XMLSignatureException is thrown by the method
     * {@link XMLSignature#validate(javax.xml.crypto.dsig.XMLValidateContext)}.
     * <p>
     * You can return more specific exceptions which are useful for your
     * use-case.
     * 
     * @param e exception
     * @return exception exception which is then thrown by XmlSignerProcessor.
     */
    Exception onXMLSignatureException(XMLSignatureException e);

    void start();

    void signatureValueValidationFailed(SignatureValue value) throws Exception; // NOPMD

    void referenceValidationFailed(Reference ref) throws Exception; // NOPMD

    void manifestReferenceValidationFailed(Reference ref) throws Exception; // NOPMD

    /**
     * If <tt>true</tt> is returned then the verifier will go-on as if there was
     * no validation failure. If <tt>false</tt> is returned than the verifier
     * will throw an {@link XmlSignatureInvalidException}.
     * <p>
     * Best practice is to return <code>false</code> to ensure that after a core
     * validation failure, the verification fails.
     * 
     * @return true or false
     * @throws Exception
     */
    boolean ignoreCoreValidationFailure() throws Exception; // NOPMD

    void end() throws Exception; // NOPMD

}
