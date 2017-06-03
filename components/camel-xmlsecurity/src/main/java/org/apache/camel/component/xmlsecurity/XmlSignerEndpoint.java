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

import java.util.List;

import javax.xml.crypto.AlgorithmMethod;
import javax.xml.crypto.dsig.spec.XPathFilterParameterSpec;

import org.apache.camel.Processor;
import org.apache.camel.component.xmlsecurity.api.KeyAccessor;
import org.apache.camel.component.xmlsecurity.api.XmlSignatureProperties;
import org.apache.camel.component.xmlsecurity.processor.XmlSignerConfiguration;
import org.apache.camel.component.xmlsecurity.processor.XmlSignerProcessor;

public class XmlSignerEndpoint extends XmlSignatureEndpoint {

    private XmlSignerConfiguration configuration;

    public XmlSignerEndpoint(String uri, XmlSignatureComponent component, XmlSignerConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    Processor createProcessor() {
        return new XmlSignerProcessor(getConfiguration());
    }

    public XmlSignerConfiguration getConfiguration() {
        return configuration;
    }

    public void setConfiguration(XmlSignerConfiguration configuration) {
        this.configuration = configuration;
    }

    public KeyAccessor getKeyAccessor() {
        return getConfiguration().getKeyAccessor();
    }

    public void setKeyAccessor(KeyAccessor keyAccessor) {
        getConfiguration().setKeyAccessor(keyAccessor);
    }

    public String getSignatureAlgorithm() {
        return getConfiguration().getSignatureAlgorithm();
    }

    public void setSignatureAlgorithm(String signatureAlgorithm) {
        getConfiguration().setSignatureAlgorithm(signatureAlgorithm);
    }

    public String getDigestAlgorithm() {
        return getConfiguration().getDigestAlgorithm();
    }

    public void setDigestAlgorithm(String digestAlgorithm) {
        getConfiguration().setDigestAlgorithm(digestAlgorithm);
    }

    public AlgorithmMethod getCanonicalizationMethod() {
        return getConfiguration().getCanonicalizationMethod();
    }

    public void setCanonicalizationMethod(AlgorithmMethod canonicalizationMethod) {
        getConfiguration().setCanonicalizationMethod(canonicalizationMethod);
    }

    public List<AlgorithmMethod> getTransformMethods() {
        return getConfiguration().getTransformMethods();
    }

    public void setTransformMethods(List<AlgorithmMethod> transformMethods) {
        getConfiguration().setTransformMethods(transformMethods);
    }

    public Boolean getAddKeyInfoReference() {
        return getConfiguration().getAddKeyInfoReference();
    }

    public void setAddKeyInfoReference(Boolean addKeyInfoReference) {
        getConfiguration().setAddKeyInfoReference(addKeyInfoReference);
    }

    public String getPrefixForXmlSignatureNamespace() {
        return getConfiguration().getPrefixForXmlSignatureNamespace();
    }

    public void setPrefixForXmlSignatureNamespace(String prefixForXmlSignatureNamespace) {
        getConfiguration().setPrefixForXmlSignatureNamespace(prefixForXmlSignatureNamespace);
    }

    public String getParentLocalName() {
        return getConfiguration().getParentLocalName();
    }

    public void setParentLocalName(String parentLocalName) {
        getConfiguration().setParentLocalName(parentLocalName);
    }

    public String getParentNamespace() {
        return getConfiguration().getParentNamespace();
    }

    public void setParentNamespace(String parentNamespace) {
        getConfiguration().setParentNamespace(parentNamespace);
    }

    public String getContentReferenceUri() {
        return getConfiguration().getContentReferenceUri();
    }

    public void setContentReferenceUri(String referenceUri) {
        getConfiguration().setContentReferenceUri(referenceUri);
    }

    public String getContentReferenceType() {
        return getConfiguration().getContentReferenceType();
    }

    public void setContentReferenceType(String referenceType) {
        getConfiguration().setContentReferenceType(referenceType);
    }

    public Boolean getPlainText() {
        return getConfiguration().getPlainText();
    }

    public void setPlainText(Boolean plainText) {
        getConfiguration().setPlainText(plainText);
    }

    public String getMessageEncoding() {
        return getConfiguration().getPlainTextEncoding();
    }

    public void setMessageEncoding(String messageEncoding) {
        getConfiguration().setPlainTextEncoding(messageEncoding);
    }

    public XmlSignatureProperties getProperties() {
        return getConfiguration().getProperties();
    }

    public void setProperties(XmlSignatureProperties properties) {
        getConfiguration().setProperties(properties);
    }

    public String getContentObjectId() {
        return getConfiguration().getContentObjectId();
    }

    public void setContentObjectId(String contentObjectId) {
        getConfiguration().setContentObjectId(contentObjectId);
    }

    public List<XPathFilterParameterSpec> getXpathsToIdAttributes() {
        return getConfiguration().getXpathsToIdAttributes();
    }

    public void setXpathsToIdAttributes(List<XPathFilterParameterSpec> xpathsToIdAttributes) {
        getConfiguration().setXpathsToIdAttributes(xpathsToIdAttributes);
    }

    public String getSignatureId() {
        return getConfiguration().getSignatureId();
    }

    public void setSignatureId(String signatureId) {
        getConfiguration().setSignatureId(signatureId);
    }
    
    public XPathFilterParameterSpec getParentXpath() {
        return getConfiguration().getParentXpath();
    }
    
    public void setParentXpath(XPathFilterParameterSpec parentXpath) {
        getConfiguration().setParentXpath(parentXpath);
    }

}
