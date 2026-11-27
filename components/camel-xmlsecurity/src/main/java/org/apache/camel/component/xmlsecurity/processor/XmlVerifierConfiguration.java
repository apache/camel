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
package org.apache.camel.component.xmlsecurity.processor;

import javax.xml.crypto.KeySelector;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.xmlsecurity.api.DefaultValidationFailedHandler;
import org.apache.camel.component.xmlsecurity.api.DefaultXmlSignature2Message;
import org.apache.camel.component.xmlsecurity.api.ValidationFailedHandler;
import org.apache.camel.component.xmlsecurity.api.XmlSignature2Message;
import org.apache.camel.component.xmlsecurity.api.XmlSignatureChecker;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@UriParams
public class XmlVerifierConfiguration extends XmlSignatureConfiguration {

    @UriParam(description = "The javax.xml.crypto.KeySelector that provides the key to validate each Signature element with."
                            + " Required: the verifier fails without it. DefaultKeySelector of the package"
                            + " org.apache.camel.component.xmlsecurity.api returns the public key of the certificate of an alias in"
                            + " a java.security.KeyStore, regardless of the KeyInfo of the signature.")
    private KeySelector keySelector;
    @UriParam(description = "A bean that implements org.apache.camel.component.xmlsecurity.api.XmlSignatureChecker, which the"
                            + " verifier calls for each Signature element before its core validation, to check that the signature"
                            + " covers the content that the route expects (see"
                            + " http://www.w3.org/TR/xmldsig-bestpractices/#check-what-is-signed). An exception that it throws fails"
                            + " the exchange. If not set, no such check is done.")
    private XmlSignatureChecker xmlSignatureChecker;
    @UriParam(description = "Maps the verified document to the body of the output message. The default"
                            + " DefaultXmlSignature2Message selects the output node with outputNodeSearchType, outputNodeSearch and"
                            + " removeSignatureElements. Its Default search returns the content of the single referenced Object"
                            + " element of an enveloping signature, and the whole document without its Signature elements for an enveloped or"
                            + " detached signature, which can contain content that no reference covers unless its"
                            + " enforceReferenceCoverage is set.")
    private XmlSignature2Message xmlSignature2Message = new DefaultXmlSignature2Message();
    @UriParam(description = "Called when the core validation of a signature fails or throws an XMLSignatureException. The default"
                            + " DefaultValidationFailedHandler throws an XmlSignatureInvalidValueException when the signature value"
                            + " is invalid, an XmlSignatureInvalidContentHashException when the digest of a reference or of a"
                            + " manifest reference does not match, and an XmlSignatureInvalidKeyException or XmlSignatureException for an XMLSignatureException."
                            + " If a custom handler returns true from ignoreCoreValidationFailure, the verifier treats the signature"
                            + " as valid.")
    private ValidationFailedHandler validationFailedHandler = new DefaultValidationFailedHandler();
    @UriParam(description = "The value that the output node search of outputNodeSearchType uses: for ElementName a String in the"
                            + " form {namespace}localName, or localName for an element without a namespace, and for XPath a"
                            + " javax.xml.crypto.dsig.spec.XPathFilterParameterSpec. The search must find exactly one node. Not used"
                            + " by the Default search.")
    private Object outputNodeSearch;
    @UriParam(defaultValue = DefaultXmlSignature2Message.OUTPUT_NODE_SEARCH_TYPE_DEFAULT,
              description = "How the default xmlSignature2Message finds the node that becomes the output body: Default,"
                            + " ElementName (the element that outputNodeSearch names) or XPath (the element, text or document node"
                            + " that the XPath of outputNodeSearch selects). Default returns the content of the single referenced"
                            + " Object element of an enveloping signature, and the whole document without its Signature elements for an"
                            + " enveloped or detached signature.")
    private String outputNodeSearchType = DefaultXmlSignature2Message.OUTPUT_NODE_SEARCH_TYPE_DEFAULT;
    @UriParam(defaultValue = "false",
              description = "Whether the default xmlSignature2Message removes all Signature elements of the namespace"
                            + " http://www.w3.org/2000/09/xmldsig# from the document before the output node is serialized. For the"
                            + " ElementName and XPath searches of an enveloped signature; the Default search always removes them for"
                            + " an enveloped or detached signature.")
    private Boolean removeSignatureElements = Boolean.FALSE;
    @UriParam(defaultValue = "true",
              description = "Whether the verifier sets the properties org.apache.jcp.xml.dsig.secureValidation and"
                            + " org.jcp.xml.dsig.secureValidation to true, which makes the XML signature provider reject, for"
                            + " example, MD5 algorithms, more than 30 references and more than 5 transforms per reference. If false,"
                            + " the verifier does not set them and the default of the provider applies, which for the Apache"
                            + " Santuario provider is also secure validation.")
    private Boolean secureValidation = Boolean.TRUE;

    public XmlVerifierConfiguration() {
    }

    public XmlVerifierConfiguration copy() {
        try {
            return (XmlVerifierConfiguration) clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }

    public void setKeySelector(KeySelector keySelector) {
        this.keySelector = keySelector;
    }

    public KeySelector getKeySelector() {
        return keySelector;
    }

    public XmlSignatureChecker getXmlSignatureChecker() {
        return xmlSignatureChecker;
    }

    public void setXmlSignatureChecker(XmlSignatureChecker xmlSignatureChecker) {
        this.xmlSignatureChecker = xmlSignatureChecker;
    }

    public XmlSignature2Message getXmlSignature2Message() {
        return xmlSignature2Message;
    }

    public void setXmlSignature2Message(XmlSignature2Message xmlSignature2Message) {
        this.xmlSignature2Message = xmlSignature2Message;
    }

    public ValidationFailedHandler getValidationFailedHandler() {
        return validationFailedHandler;
    }

    public void setValidationFailedHandler(ValidationFailedHandler validationFailedHandler) {
        this.validationFailedHandler = validationFailedHandler;
    }

    public Object getOutputNodeSearch() {
        return outputNodeSearch;
    }

    public void setOutputNodeSearch(Object outputNodeSearch) {
        this.outputNodeSearch = outputNodeSearch;
    }

    public String getOutputNodeSearchType() {
        return outputNodeSearchType;
    }

    public void setOutputNodeSearchType(String outputNodeSearchType) {
        this.outputNodeSearchType = outputNodeSearchType;
    }

    public Boolean getRemoveSignatureElements() {
        return removeSignatureElements;
    }

    public void setRemoveSignatureElements(Boolean removeSignatureElements) {
        this.removeSignatureElements = removeSignatureElements;
    }

    public Boolean getSecureValidation() {
        return secureValidation;
    }

    public void setSecureValidation(Boolean secureValidation) {
        this.secureValidation = secureValidation;
    }

}
