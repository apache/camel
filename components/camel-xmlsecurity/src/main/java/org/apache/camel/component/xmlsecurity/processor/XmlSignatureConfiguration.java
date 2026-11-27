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

import java.util.Map;

import javax.xml.crypto.URIDereferencer;

import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@UriParams
public abstract class XmlSignatureConfiguration implements Cloneable {

    @UriParam(label = "producer",
              description = "The base URI that relative URIs of the references are resolved against, set on the XMLSignContext of"
                            + " the signer and the XMLValidateContext of the verifier (XMLCryptoContext.setBaseURI).")
    private String baseUri;
    @UriParam(label = "producer",
              description = "Properties that are set on the XMLSignContext of the signer and the XMLValidateContext of the"
                            + " verifier, see XMLCryptoContext.setProperty and the properties that the XML signature provider"
                            + " supports. The verifier sets javax.xml.crypto.dsig.cacheReference and"
                            + " org.jcp.xml.dsig.validateManifests to true itself; these properties are set after that, so a value"
                            + " given here replaces the value that the verifier sets for the same property.")
    private Map<String, ?> cryptoContextProperties;
    @UriParam(label = "producer", defaultValue = "true",
              description = "Whether the parser rejects a message body that contains a DOCTYPE declaration. If false, a DOCTYPE"
                            + " declaration is allowed; external entities stay disabled either way.")
    private Boolean disallowDoctypeDecl = Boolean.TRUE;
    @UriParam(label = "producer", defaultValue = "false",
              description = "Whether to leave out the XML declaration of the output body. Has no effect when the output of the"
                            + " verifier is text. The CamelXmlSignatureOmitXmlDeclaration header replaces this option.")
    private Boolean omitXmlDeclaration = Boolean.FALSE;
    @UriParam(label = "producer", defaultValue = "true",
              description = "Whether to remove the CamelXmlSignature headers of this component from the message after signing or"
                            + " verifying. The CamelCharsetName header is kept.")
    private Boolean clearHeaders = Boolean.TRUE;
    @UriParam(label = "producer",
              description = "The XML schema that the message body is validated against while it is parsed, loaded as a resource"
                            + " from the classpath by default. The schema also declares which attributes are of type ID, so it is"
                            + " required for a detached signature and when a reference URI points to an ID. The"
                            + " CamelXmlSignatureSchemaResourceUri header replaces this option.")
    @Metadata(supportFileReference = true)
    private String schemaResourceUri;
    @UriParam(label = "producer",
              description = "The character encoding of the output body. If not set, UTF-8 is used. If set, the CamelCharsetName"
                            + " header is also set to it; for the verifier, the default xmlSignature2Message does this.")
    private String outputXmlEncoding;
    @UriParam(label = "advanced",
              description = "To use a custom javax.xml.crypto.URIDereferencer that resolves the URIs of the references, for"
                            + " example to control which resources can be read. If not set, the dereferencer of the XML signature"
                            + " provider is used; the Apache Santuario provider, which the component uses, resolves only"
                            + " same-document URIs (fragments and XPointer) by default.")
    private URIDereferencer uriDereferencer;

    protected XmlSignatureConfiguration() {
    }

    public URIDereferencer getUriDereferencer() {
        return uriDereferencer;
    }

    public void setUriDereferencer(URIDereferencer uriDereferencer) {
        this.uriDereferencer = uriDereferencer;
    }

    public String getBaseUri() {
        return baseUri;
    }

    public void setBaseUri(String baseUri) {
        this.baseUri = baseUri;
    }

    public Map<String, ? extends Object> getCryptoContextProperties() {
        return cryptoContextProperties;
    }

    public void setCryptoContextProperties(Map<String, ? extends Object> cryptoContextProperties) {
        this.cryptoContextProperties = cryptoContextProperties;
    }

    public Boolean getDisallowDoctypeDecl() {
        return disallowDoctypeDecl;
    }

    public void setDisallowDoctypeDecl(Boolean disallowDoctypeDecl) {
        this.disallowDoctypeDecl = disallowDoctypeDecl;
    }

    public Boolean getOmitXmlDeclaration() {
        return omitXmlDeclaration;
    }

    public void setOmitXmlDeclaration(Boolean omitXmlDeclaration) {
        this.omitXmlDeclaration = omitXmlDeclaration;
    }

    public Boolean getClearHeaders() {
        return clearHeaders;
    }

    public void setClearHeaders(Boolean clearHeaders) {
        this.clearHeaders = clearHeaders;
    }

    public String getSchemaResourceUri() {
        return schemaResourceUri;
    }

    public void setSchemaResourceUri(String schemaResourceUri) {
        this.schemaResourceUri = schemaResourceUri;
    }

    public String getOutputXmlEncoding() {
        return outputXmlEncoding;
    }

    public void setOutputXmlEncoding(String outputXmlEncoding) {
        this.outputXmlEncoding = outputXmlEncoding;
    }

}
