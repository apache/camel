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

public class XmlVerifierConfiguration extends XmlSignatureConfiguration {

    @UriParam
    private KeySelector keySelector;

    @UriParam
    private String keySelectorName;

    @UriParam
    private XmlSignatureChecker xmlSignatureChecker;

    @UriParam
    private String xmlSignatureCheckerName;

    @UriParam
    private XmlSignature2Message xmlSignature2Message = new DefaultXmlSignature2Message();

    @UriParam
    private String xmlSignature2MessageName;

    @UriParam
    private ValidationFailedHandler validationFailedHandler = new DefaultValidationFailedHandler();

    @UriParam
    private String validationFailedHandlerName;

    @UriParam
    private Object outputNodeSearch;

    @UriParam(defaultValue = DefaultXmlSignature2Message.OUTPUT_NODE_SEARCH_TYPE_DEFAULT)
    private String outputNodeSearchType = DefaultXmlSignature2Message.OUTPUT_NODE_SEARCH_TYPE_DEFAULT;

    @UriParam(defaultValue = "Boolean.FALSE")
    private Boolean removeSignatureElements = Boolean.FALSE;

    @UriParam(defaultValue = "Boolean.TRUE")
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

    public void setKeySelector(KeySelector keySelector) {
        this.keySelector = keySelector;
    }

    public KeySelector getKeySelector() {
        return keySelector;
    }

    /**
     * Sets the reference name for a KeySelector that can be found in the
     * registry.
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

    public void setXmlSignatureChecker(XmlSignatureChecker xmlSignatureChecker) {
        this.xmlSignatureChecker = xmlSignatureChecker;
    }

    /**
     * Sets the reference name for a application checker that can be found in
     * the registry.
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
     * 
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
     * 
     * @param outputNodeSearchType
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

    public void setSecureValidation(Boolean secureValidation) {
        this.secureValidation = secureValidation;
    }

}
