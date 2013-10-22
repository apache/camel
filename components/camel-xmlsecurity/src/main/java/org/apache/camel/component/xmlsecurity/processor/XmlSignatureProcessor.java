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

import java.lang.reflect.Field;
import java.util.Map;

import javax.xml.crypto.XMLCryptoContext;

import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.component.xmlsecurity.SantuarioUtil;
import org.apache.camel.component.xmlsecurity.api.XmlSignatureConstants;
import org.apache.camel.util.ObjectHelper;
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
}
