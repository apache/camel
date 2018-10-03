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
import org.apache.camel.component.xmlsecurity.processor.XmlSignerConfiguration;
import org.apache.camel.component.xmlsecurity.processor.XmlVerifierConfiguration;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;

/**
 * Used to sign and verify exchanges using the XML signature specification.
 */
@UriEndpoint(firstVersion = "2.12.0", scheme = "xmlsecurity", title = "XML Security", syntax = "xmlsecurity:command:name", producerOnly = true, label = "security,transformation")
public abstract class XmlSignatureEndpoint extends DefaultEndpoint {

    @UriPath @Metadata(required = "true")
    private XmlCommand command;
    @UriPath @Metadata(required = "true")
    private String name;
    // to include both kind of configuration params
    @UriParam
    private XmlSignerConfiguration signerConfiguration;
    @UriParam
    private XmlVerifierConfiguration verifierConfiguration;

    public XmlSignatureEndpoint(String uri, XmlSignatureComponent component) {
        super(uri, component);
    }

    public XmlCommand getCommand() {
        return command;
    }

    /**
     * Whether to sign or verify.
     */
    public void setCommand(XmlCommand command) {
        this.command = command;
    }

    public String getName() {
        return name;
    }

    /**
     * The name part in the URI can be chosen by the user to distinguish between different signer/verifier endpoints within the camel context.
     */
    public void setName(String name) {
        this.name = name;
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
