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
import javax.xml.crypto.XMLCryptoContext;
import javax.xml.crypto.dsig.XMLSignContext;
import javax.xml.crypto.dsig.XMLValidateContext;

import org.apache.camel.component.xmlsecurity.api.XmlSignatureConstants;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;

@UriParams
public abstract class XmlSignatureConfiguration implements Cloneable {

    @UriParam(label = "producer")
    private String baseUri;
    @UriParam(label = "producer")
    private Map<String, ?> cryptoContextProperties;
    @UriParam(label = "producer", defaultValue = "true")
    private Boolean disallowDoctypeDecl = Boolean.TRUE;
    @UriParam(label = "producer", defaultValue = "false")
    private Boolean omitXmlDeclaration = Boolean.FALSE;
    @UriParam(label = "producer", defaultValue = "true")
    private Boolean clearHeaders = Boolean.TRUE;
    @UriParam(label = "producer")
    private String schemaResourceUri;
    @UriParam(label = "producer")
    private String outputXmlEncoding;
    @UriParam(label = "advanced")
    private URIDereferencer uriDereferencer;

    public XmlSignatureConfiguration() {
    }

    public URIDereferencer getUriDereferencer() {
        return uriDereferencer;
    }

    /**
     * If you want to restrict the remote access via reference URIs, you can set
     * an own dereferencer. Optional parameter. If not set the provider default
     * dereferencer is used which can resolve URI fragments, HTTP, file and
     * XPpointer URIs.
     * <p>
     * Attention: The implementation is provider dependent!
     * 
     * @see XMLCryptoContext#setURIDereferencer(URIDereferencer)
     */
    public void setUriDereferencer(URIDereferencer uriDereferencer) {
        this.uriDereferencer = uriDereferencer;
    }

    public String getBaseUri() {
        return baseUri;
    }

    /**
     * You can set a base URI which is used in the URI dereferencing. Relative
     * URIs are then concatenated with the base URI.
     *
     * @see XMLCryptoContext#setBaseURI(String)
     */
    public void setBaseUri(String baseUri) {
        this.baseUri = baseUri;
    }

    public Map<String, ? extends Object> getCryptoContextProperties() {
        return cryptoContextProperties;
    }

    /**
     * Sets the crypto context properties. See
     * {@link XMLCryptoContext#setProperty(String, Object)}. Possible properties
     * are defined in {@link XMLSignContext} an {@link XMLValidateContext} (see
     * Supported Properties).
     * <p>
     * The following properties are set by default to the value
     * {@link Boolean#TRUE} for the XML validation. If you want to switch these
     * features off you must set the property value to {@link Boolean#FALSE}.
     * <ul>
     * <li><code>"org.jcp.xml.dsig.validateManifests"</code></li>
     * <li><code>"javax.xml.crypto.dsig.cacheReference"</code></li>
     * </ul>
     */
    public void setCryptoContextProperties(Map<String, ? extends Object> cryptoContextProperties) {
        this.cryptoContextProperties = cryptoContextProperties;
    }

    public Boolean getDisallowDoctypeDecl() {
        return disallowDoctypeDecl;
    }

    /**
     * Disallows that the incoming XML document contains DTD DOCTYPE
     * declaration. The default value is {@link Boolean#TRUE}.
     * 
     * @param disallowDoctypeDecl if set to {@link Boolean#FALSE} then DOCTYPE declaration is allowed, otherwise not
     */
    public void setDisallowDoctypeDecl(Boolean disallowDoctypeDecl) {
        this.disallowDoctypeDecl = disallowDoctypeDecl;
    }

    public Boolean getOmitXmlDeclaration() {
        return omitXmlDeclaration;
    }

    /**
     * Indicator whether the XML declaration in the outgoing message body should
     * be omitted. Default value is <code>false</code>. Can be overwritten by
     * the header {@link XmlSignatureConstants#HEADER_OMIT_XML_DECLARATION}.
     */
    public void setOmitXmlDeclaration(Boolean omitXmlDeclaration) {
        this.omitXmlDeclaration = omitXmlDeclaration;
    }

    /**
     * Determines if the XML signature specific headers be cleared after signing
     * and verification. Defaults to true.
     * 
     * @return true if the Signature headers should be unset, false otherwise
     */
    public Boolean getClearHeaders() {
        return clearHeaders;
    }

    /**
     * Determines if the XML signature specific headers be cleared after signing
     * and verification. Defaults to true.
     */
    public void setClearHeaders(Boolean clearHeaders) {
        this.clearHeaders = clearHeaders;
    }

    public String getSchemaResourceUri() {
        return schemaResourceUri;
    }

    /**
     * Classpath to the XML Schema. Must be specified in the detached XML
     * Signature case for determining the ID attributes, might be set in the
     * enveloped and enveloping case. If set, then the XML document is validated
     * with the specified XML schema. The schema resource URI can be overwritten
     * by the header {@link XmlSignatureConstants#HEADER_SCHEMA_RESOURCE_URI}.
     */
    public void setSchemaResourceUri(String schemaResourceUri) {
        this.schemaResourceUri = schemaResourceUri;
    }
    
    public String getOutputXmlEncoding() {
        return outputXmlEncoding;
    }

    /**
     * The character encoding of the resulting signed XML document. If
     * <code>null</code> then the encoding of the original XML document is used.
     */
    public void setOutputXmlEncoding(String outputXmlEncoding) {
        this.outputXmlEncoding = outputXmlEncoding;
    }

}
