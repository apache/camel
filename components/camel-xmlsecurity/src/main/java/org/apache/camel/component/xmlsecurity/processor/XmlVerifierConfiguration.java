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
package org.apache.camel.component.xmlsecurity.processor;

import javax.xml.crypto.KeySelector;

import org.apache.camel.CamelContext;
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

    @UriParam(label = "verify")
    private KeySelector keySelector;
    private String keySelectorName;
    @UriParam(label = "verify")
    private XmlSignatureChecker xmlSignatureChecker;
    private String xmlSignatureCheckerName;
    @UriParam(label = "verify")
    private XmlSignature2Message xmlSignature2Message = new DefaultXmlSignature2Message();
    private String xmlSignature2MessageName;
    @UriParam(label = "verify")
    private ValidationFailedHandler validationFailedHandler = new DefaultValidationFailedHandler();
    private String validationFailedHandlerName;
    @UriParam(label = "verify", javaType = "java.lang.String")
    private Object outputNodeSearch;
    @UriParam(label = "verify", defaultValue = DefaultXmlSignature2Message.OUTPUT_NODE_SEARCH_TYPE_DEFAULT)
    private String outputNodeSearchType = DefaultXmlSignature2Message.OUTPUT_NODE_SEARCH_TYPE_DEFAULT;
    @UriParam(label = "verify", defaultValue = "false")
    private Boolean removeSignatureElements = Boolean.FALSE;
    @UriParam(label = "verify", defaultValue = "true")
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

    public void setCamelContext(CamelContext camelContext) {
        super.setCamelContext(camelContext);
        setKeySelector(keySelectorName);
        setXmlSignatureChecker(xmlSignatureCheckerName);
        setXmlSignature2Message(xmlSignature2MessageName);
        setValidationFailedHandler(validationFailedHandlerName);
    }

    /**
     * Provides the key for validating the XML signature.
     */
    public void setKeySelector(KeySelector keySelector) {
        this.keySelector = keySelector;
    }

    public KeySelector getKeySelector() {
        return keySelector;
    }

    /**
     * Sets the reference name for a KeySelector that can be found in the registry.
     */
    public void setKeySelector(String keySelectorName) {
        if (getCamelContext() != null && keySelectorName != null) {
            KeySelector selector = getCamelContext().getRegistry()
                .lookupByNameAndType(keySelectorName, KeySelector.class);
            if (selector != null) {
                setKeySelector(selector);
            }
        }
        if (keySelectorName != null) {
            this.keySelectorName = keySelectorName;
        }
    }

    public XmlSignatureChecker getXmlSignatureChecker() {
        return xmlSignatureChecker;
    }

    /**
     * This interface allows the application to check the XML signature before the validation is executed.
     * This step is recommended in http://www.w3.org/TR/xmldsig-bestpractices/#check-what-is-signed
     */
    public void setXmlSignatureChecker(XmlSignatureChecker xmlSignatureChecker) {
        this.xmlSignatureChecker = xmlSignatureChecker;
    }

    /**
     * Sets the reference name for a application checker that can be found in the registry.
     */
    public void setXmlSignatureChecker(String xmlSignatureCheckerName) {
        if (getCamelContext() != null && xmlSignatureCheckerName != null) {
            XmlSignatureChecker checker = getCamelContext().getRegistry()
                .lookupByNameAndType(xmlSignatureCheckerName,
                                     XmlSignatureChecker.class);
            if (checker != null) {
                setXmlSignatureChecker(checker);
            }
        }
        if (xmlSignatureCheckerName != null) {
            this.xmlSignatureCheckerName = xmlSignatureCheckerName;
        }
    }

    public XmlSignature2Message getXmlSignature2Message() {
        return xmlSignature2Message;
    }

    /**
     * Bean which maps the XML signature to the output-message after the validation.
     * How this mapping should be done can be configured by the options outputNodeSearchType, outputNodeSearch, and removeSignatureElements.
     * The default implementation offers three possibilities which are related to the three output node search types "Default", "ElementName", and "XPath".
     * The default implementation determines a node which is then serialized and set to the body of the output message
     * If the search type is "ElementName" then the output node (which must be in this case an element) is determined
     * by the local name and namespace defined in the search value (see option outputNodeSearch).
     * If the search type is "XPath" then the output node is determined by the XPath specified in the search value
     * (in this case the output node can be of type "Element", "TextNode" or "Document").
     * If the output node search type is "Default" then the following rules apply:
     * In the enveloped XML signature case (there is a reference with URI="" and transform "http://www.w3.org/2000/09/xmldsig#enveloped-signature"),
     * the incoming XML document without the Signature element is set to the output message body.
     * In the non-enveloped XML signature case, the message body is determined from a referenced Object;
     * this is explained in more detail in chapter "Output Node Determination in Enveloping XML Signature Case".
     */
    public void setXmlSignature2Message(XmlSignature2Message xmlSignature2Message) {
        this.xmlSignature2Message = xmlSignature2Message;
    }

    /**
     * Sets the reference name for the to-message instance that can be found in
     * the registry.
     */
    public void setXmlSignature2Message(String xmlSignature2Message) {
        if (getCamelContext() != null && xmlSignature2Message != null) {
            XmlSignature2Message maper = getCamelContext().getRegistry()
                .lookupByNameAndType(xmlSignature2Message,
                                     XmlSignature2Message.class);
            if (maper != null) {
                setXmlSignature2Message(maper);
            }
        }
        if (xmlSignature2Message != null) {
            this.xmlSignature2MessageName = xmlSignature2Message;
        }
    }

    public ValidationFailedHandler getValidationFailedHandler() {
        return validationFailedHandler;
    }

    /**
     * Handles the different validation failed situations.
     * The default implementation throws specific exceptions for the different situations
     * (All exceptions have the package name org.apache.camel.component.xmlsecurity.api and are a sub-class of XmlSignatureInvalidException.
     * If the signature value validation fails, a XmlSignatureInvalidValueException is thrown.
     * If a reference validation fails, a XmlSignatureInvalidContentHashException is thrown. For more detailed information, see the JavaDoc.
     */
    public void setValidationFailedHandler(ValidationFailedHandler validationFailedHandler) {
        this.validationFailedHandler = validationFailedHandler;
    }

    public void setValidationFailedHandler(String validationFailedHandlerName) {
        if (getCamelContext() != null && validationFailedHandlerName != null) {
            ValidationFailedHandler vailFailedHandler = getCamelContext()
                .getRegistry().lookupByNameAndType(validationFailedHandlerName,
                            ValidationFailedHandler.class);
            if (vailFailedHandler != null) {
                setValidationFailedHandler(vailFailedHandler);
            }
        }
        if (validationFailedHandlerName != null) {
            this.validationFailedHandlerName = validationFailedHandlerName;
        }
    }

    public Object getOutputNodeSearch() {
        return outputNodeSearch;
    }

    /**
     * Sets the output node search value for determining the node from the XML
     * signature document which shall be set to the output message body. The
     * class of the value depends on the type of the output node search. The
     * output node search is forwarded to {@link XmlSignature2Message}.
     */
    public void setOutputNodeSearch(Object outputNodeSearch) {
        this.outputNodeSearch = outputNodeSearch;
    }

    public String getOutputNodeSearchType() {
        return outputNodeSearchType;
    }

    /**
     * Determines the search type for determining the output node which is
     * serialized into the output message bodyF. See
     * {@link #setOutputNodeSearch(Object)}. The supported default search types
     * you can find in {@link DefaultXmlSignature2Message}.
     */
    public void setOutputNodeSearchType(String outputNodeSearchType) {
        this.outputNodeSearchType = outputNodeSearchType;
    }

    public Boolean getRemoveSignatureElements() {
        return removeSignatureElements;
    }

    /**
     * Indicator whether the XML signature elements (elements with local name
     * "Signature" and namesapce ""http://www.w3.org/2000/09/xmldsig#"") shall
     * be removed from the document set to the output message. Normally, this is
     * only necessary, if the XML signature is enveloped. The default value is
     * {@link Boolean#FALSE}. This parameter is forwarded to
     * {@link XmlSignature2Message}.
     * <p>
     * This indicator has no effect if the output node search is of type
     * {@link DefaultXmlSignature2Message#OUTPUT_NODE_SEARCH_TYPE_DEFAULT}.F
     */
    public void setRemoveSignatureElements(Boolean removeSignatureElements) {
        this.removeSignatureElements = removeSignatureElements;
    }

    public Boolean getSecureValidation() {
        return secureValidation;
    }

    /**
     * Enables secure validation. If true then secure validation is enabled.
     */
    public void setSecureValidation(Boolean secureValidation) {
        this.secureValidation = secureValidation;
    }

    public String getValidationFailedHandlerName() {
        return validationFailedHandlerName;
    }

    /**
     * Name of handler to
     * @param validationFailedHandlerName
     */
    public void setValidationFailedHandlerName(String validationFailedHandlerName) {
        this.validationFailedHandlerName = validationFailedHandlerName;
    }
}
