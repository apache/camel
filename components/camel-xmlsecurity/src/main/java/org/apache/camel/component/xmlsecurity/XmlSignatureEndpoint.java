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

import java.util.Map;

import javax.xml.crypto.URIDereferencer;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.xmlsecurity.processor.XmlSignatureConfiguration;
import org.apache.camel.impl.DefaultEndpoint;

public abstract class XmlSignatureEndpoint extends DefaultEndpoint {

    public XmlSignatureEndpoint(String uri, XmlSignatureComponent component) {
        super(uri, component);
    }

    @Override
    public Producer createProducer() {
        return new XmlSignatureProducer(this, createProcessor());
    }

    abstract Processor createProcessor();

    @Override
    public Consumer createConsumer(Processor processor) {
        throw new UnsupportedOperationException("XML Signature endpoints are not meant to be consumed from.");
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    public Object getManagedObject(XmlSignatureEndpoint endpoint) {
        return this;
    }

    public abstract XmlSignatureConfiguration getConfiguration();

    public URIDereferencer getUriDereferencer() {
        return getConfiguration().getUriDereferencer();
    }

    public void setUriDereferencer(URIDereferencer uriDereferencer) {
        getConfiguration().setUriDereferencer(uriDereferencer);
    }

    public String getBaseUri() {
        return getConfiguration().getBaseUri();
    }

    public void setBaseUri(String baseUri) {
        getConfiguration().setBaseUri(baseUri);
    }

    public Map<String, ? extends Object> getCryptoContextProperties() {
        return getConfiguration().getCryptoContextProperties();
    }

    public void setCryptoContextProperties(Map<String, ? extends Object> cryptoContextProperties) {
        getConfiguration().setCryptoContextProperties(cryptoContextProperties);
    }

    public Boolean getDisallowDoctypeDecl() {
        return getConfiguration().getDisallowDoctypeDecl();
    }

    public void setDisallowDoctypeDecl(Boolean disallowDoctypeDecl) {
        getConfiguration().setDisallowDoctypeDecl(disallowDoctypeDecl);
    }

    public Boolean getOmitXmlDeclaration() {
        return getConfiguration().getOmitXmlDeclaration();
    }

    public void setOmitXmlDeclaration(Boolean omitXmlDeclaration) {
        getConfiguration().setOmitXmlDeclaration(omitXmlDeclaration);
    }
    
    public String getSchemaResourceUri() {
        return getConfiguration().getSchemaResourceUri();
    }

    public void setSchemaResourceUri(String schemaResourceUri) {
        getConfiguration().setSchemaResourceUri(schemaResourceUri);
        
    }
    
    public String getOutputXmlEncoding() {
        return getConfiguration().getOutputXmlEncoding();
    }
    
    public void setOutputXmlEncoding(String encoding) {
        getConfiguration().setOutputXmlEncoding(encoding);
    }
    
}
