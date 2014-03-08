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
package org.apache.camel.component.xmlsecurity;

import javax.xml.crypto.KeySelector;

import org.apache.camel.Processor;
import org.apache.camel.component.xmlsecurity.api.ValidationFailedHandler;
import org.apache.camel.component.xmlsecurity.api.XmlSignature2Message;
import org.apache.camel.component.xmlsecurity.api.XmlSignatureChecker;
import org.apache.camel.component.xmlsecurity.processor.XmlVerifierConfiguration;
import org.apache.camel.component.xmlsecurity.processor.XmlVerifierProcessor;

public class XmlVerifierEndpoint extends XmlSignatureEndpoint {

    private XmlVerifierConfiguration configuration;

    public XmlVerifierEndpoint(String uri, XmlSignatureComponent component,
                               XmlVerifierConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    Processor createProcessor() {
        return new XmlVerifierProcessor(getConfiguration());
    }

    public XmlVerifierConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(XmlVerifierConfiguration configuration) {
        this.configuration = configuration;
    }

    public void setKeySelector(KeySelector keySelector) {
        getConfiguration().setKeySelector(keySelector);
    }

    public KeySelector getKeySelector() {
        return getConfiguration().getKeySelector();
    }

    public XmlSignatureChecker getXmlSignatureChecker() {
        return getConfiguration().getXmlSignatureChecker();
    }

    public void setXmlSignatureChecker(XmlSignatureChecker xmlSignatureChecker) {
        getConfiguration().setXmlSignatureChecker(xmlSignatureChecker);
    }

    public XmlSignature2Message getXmlSignature2Message() {
        return getConfiguration().getXmlSignature2Message();
    }

    public void setXmlSignature2Message(XmlSignature2Message xmlSignature2Message) {
        getConfiguration().setXmlSignature2Message(xmlSignature2Message);
    }

    public ValidationFailedHandler getValidationFailedHandler() {
        return getConfiguration().getValidationFailedHandler();
    }

    public void setValidationFailedHandler(ValidationFailedHandler validationFailedHandler) {
        getConfiguration().setValidationFailedHandler(validationFailedHandler);
    }

    public Object getOutputNodeSearch() {
        return getConfiguration().getOutputNodeSearch();
    }

    public void setOutputNodeSearch(Object outputNodeSearch) {
        getConfiguration().setOutputNodeSearch(outputNodeSearch);
    }

    public String getOutputNodeSearchType() {
        return getConfiguration().getOutputNodeSearchType();
    }

    public void setOutputNodeSearchType(String outputNodeSearchType) {
        getConfiguration().setOutputNodeSearchType(outputNodeSearchType);
    }

    public Boolean getRemoveSignatureElements() {
        return getConfiguration().getRemoveSignatureElements();
    }

    public void setRemoveSignatureElements(Boolean removeSignatureElements) {
        getConfiguration().setRemoveSignatureElements(removeSignatureElements);
    }

}
