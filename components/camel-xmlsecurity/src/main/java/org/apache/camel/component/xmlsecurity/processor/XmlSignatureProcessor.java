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

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.crypto.XMLCryptoContext;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;

import org.xml.sax.SAXException;

import org.apache.camel.CamelContext;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.component.validator.DefaultLSResourceResolver;
import org.apache.camel.component.xmlsecurity.api.XmlSignatureConstants;
import org.apache.camel.component.xmlsecurity.api.XmlSignatureException;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.util.IOHelper;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.xml.BytesSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class XmlSignatureProcessor implements Processor {

    private static final Logger LOG = LoggerFactory.getLogger(XmlSignatureProcessor.class);

    static {
        try {
            SantuarioUtil.initializeSantuario();
            SantuarioUtil.addSantuarioJSR105Provider();
        } catch (Throwable t) {
            // provider not in classpath, ignore and fall back to jre default
            LOG.info("Cannot add the SantuarioJSR105Provider due to {0}, fall back to JRE default.", t);
        }
    }

    protected final CamelContext context;

    public XmlSignatureProcessor(CamelContext context) {
        this.context = context;
    }

    public CamelContext getCamelContext() {
        return context;
    }

    public abstract XmlSignatureConfiguration getConfiguration();

    void setUriDereferencerAndBaseUri(XMLCryptoContext context) {
        setUriDereferencer(context);
        setBaseUri(context);
    }

    private void setUriDereferencer(XMLCryptoContext context) {
        if (getConfiguration().getUriDereferencer() != null) {
            context.setURIDereferencer(getConfiguration().getUriDereferencer());
            LOG.debug("URI dereferencer set");
        }
    }

    private void setBaseUri(XMLCryptoContext context) {
        if (getConfiguration().getBaseUri() != null) {
            context.setBaseURI(getConfiguration().getBaseUri());
            LOG.debug("Base URI {} set", context.getBaseURI());
        }
    }

    protected void setCryptoContextProperties(XMLCryptoContext cryptoContext) {
        Map<String, ? extends Object> props = getConfiguration().getCryptoContextProperties();
        if (props == null) {
            return;
        }
        for (String prop : props.keySet()) {
            Object val = props.get(prop);
            cryptoContext.setProperty(prop, val);
            LOG.debug("Context property {} set to value {}", prop, val);
        }
    }

    protected void clearMessageHeaders(Message message) {
        if (getConfiguration().getClearHeaders() != null && getConfiguration().getClearHeaders()) {
            Map<String, Object> headers = message.getHeaders();
            for (Field f : XmlSignatureConstants.class.getFields()) {
                headers.remove(ObjectHelper.lookupConstantFieldValue(XmlSignatureConstants.class, f.getName()));
            }
        }
    }

    protected Schema getSchema(Message message) throws SAXException, XmlSignatureException, IOException {

        String schemaResourceUri = getSchemaResourceUri(message);
        if (schemaResourceUri == null || schemaResourceUri.isEmpty()) {
            return null;
        }
        InputStream is = ResourceHelper.resolveResourceAsInputStream(getCamelContext().getClassResolver(),
                schemaResourceUri);
        if (is == null) {
            throw new XmlSignatureException(
                    "XML Signature component is wrongly configured: No XML schema found for specified schema resource URI "
                            + schemaResourceUri);
        }
        byte[] bytes;
        try {
            bytes = message.getExchange().getContext().getTypeConverter().convertTo(byte[].class, is);
        } finally {
            // and make sure to close the input stream after the schema has been loaded
            IOHelper.close(is);
        }
        SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        schemaFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        schemaFactory.setResourceResolver(new DefaultLSResourceResolver(getCamelContext(), getConfiguration()
                .getSchemaResourceUri()));
        LOG.debug("Instantiating schema for validation");
        return schemaFactory.newSchema(new BytesSource(bytes));
    }

    protected String getSchemaResourceUri(Message message) {
        String schemaResourceUri = message.getHeader(XmlSignatureConstants.HEADER_SCHEMA_RESOURCE_URI, String.class);
        if (schemaResourceUri == null) {
            schemaResourceUri = getConfiguration().getSchemaResourceUri();
        }
        LOG.debug("schema resource URI: {}", getConfiguration().getSchemaResourceUri());
        return schemaResourceUri;
    }

}
