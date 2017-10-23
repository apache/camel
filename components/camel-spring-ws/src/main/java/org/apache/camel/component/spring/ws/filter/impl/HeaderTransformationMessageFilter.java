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
package org.apache.camel.component.spring.ws.filter.impl;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.Map;

import javax.xml.transform.ErrorListener;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.builder.xml.XsltUriResolver;
import org.apache.camel.component.spring.ws.filter.MessageFilter;
import org.apache.camel.component.xslt.XsltComponent;
import org.apache.camel.component.xslt.XsltEndpoint;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ws.WebServiceMessage;
import org.springframework.ws.soap.SoapMessage;

/**
 * Message filter that transforms the header of a soap message
 */
public class HeaderTransformationMessageFilter implements MessageFilter {
    private static final String SOAP_HEADER_TRANSFORMATION_PROBLEM = "Soap header transformation problem";
    private static final Logger LOG = LoggerFactory.getLogger(HeaderTransformationMessageFilter.class);
    private String xslt;
    private boolean saxon;

    /**
     * @param xslt
     */
    public HeaderTransformationMessageFilter(String xslt) {
        super();
        this.xslt = xslt;
    }

    @Override
    public void filterProducer(Exchange exchange, WebServiceMessage webServiceMessage) {
        if (exchange != null) {
            processHeader(exchange.getContext(), exchange.getIn(), webServiceMessage);
        }
    }

    @Override
    public void filterConsumer(Exchange exchange, WebServiceMessage webServiceMessage) {
        if (exchange != null) {
            Message responseMessage = exchange.hasOut() ? exchange.getOut() : exchange.getIn();
            processHeader(exchange.getContext(), responseMessage, webServiceMessage);
        }
    }

    /**
     * Transform the header
     * @param context
     * @param inOrOut
     * @param webServiceMessage
     */
    private void processHeader(CamelContext context, Message inOrOut, WebServiceMessage webServiceMessage) {
        if (webServiceMessage instanceof SoapMessage) {
            SoapMessage soapMessage = (SoapMessage) webServiceMessage;
            try {
                XsltUriResolver resolver = new XsltUriResolver(context, xslt);
                Source stylesheetResource = resolver.resolve(xslt, null);

                TransformerFactory transformerFactory = getTransformerFactory(context);
                Transformer transformer = transformerFactory.newTransformer(stylesheetResource);

                addParameters(inOrOut, transformer);
                
                transformer.transform(soapMessage.getSoapHeader().getSource(), soapMessage.getSoapHeader().getResult());
            } catch (TransformerException e) {
                throw new RuntimeException("Cannot transform the header of the soap message", e);
            }
        }
    }

    /**
     * Adding the headers of the message as parameter to the transformer
     * 
     * @param inOrOut
     * @param transformer
     */
    private void addParameters(Message inOrOut, Transformer transformer) {
        Map<String, Object> headers = inOrOut.getHeaders();
        for (Map.Entry<String, Object> headerEntry : headers.entrySet()) {
            String key = headerEntry.getKey();

            // Key's with '$' are not allowed in XSLT
            if (key != null && !key.startsWith("$")) {
                transformer.setParameter(key, String.valueOf(headerEntry.getValue()));
            }
        }
    }

    /**
     * Getting a {@link TransformerFactory} with logging
     *
     * @return {@link TransformerFactory}
     */
    private TransformerFactory getTransformerFactory(CamelContext context) {
        TransformerFactory transformerFactory = null;
        if (saxon) {
            transformerFactory = getSaxonTransformerFactory(context);
        } else {
            transformerFactory = TransformerFactory.newInstance();
        }

        if (transformerFactory == null) {
            throw new IllegalStateException("Cannot resolve a transformer factory");
        }

        transformerFactory.setErrorListener(new ErrorListener() {

            @Override
            public void warning(TransformerException exception) throws TransformerException {
                LOG.warn(SOAP_HEADER_TRANSFORMATION_PROBLEM, exception);
            }

            @Override
            public void fatalError(TransformerException exception) throws TransformerException {
                LOG.error(SOAP_HEADER_TRANSFORMATION_PROBLEM, exception);
            }

            @Override
            public void error(TransformerException exception) throws TransformerException {
                LOG.error(SOAP_HEADER_TRANSFORMATION_PROBLEM, exception);
            }
        });

        return transformerFactory;
    }

    /**
     * Loading the saxon transformer class
     * 
     * @param context
     * @return
     */
    private TransformerFactory getSaxonTransformerFactory(CamelContext context) {
        final ClassResolver resolver = context.getClassResolver();
        try {
            Class<TransformerFactory> factoryClass = resolver.resolveMandatoryClass(
                    XsltEndpoint.SAXON_TRANSFORMER_FACTORY_CLASS_NAME, TransformerFactory.class,
                    XsltComponent.class.getClassLoader());

            if (factoryClass != null) {
                return ObjectHelper.newInstance(factoryClass);
            }

        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Cannot load the saxon transformer class", e);
        }

        return null;
    }

    public String getXslt() {
        return xslt;
    }

    public void setXslt(String xslt) {
        this.xslt = xslt;
    }

    public boolean isSaxon() {
        return saxon;
    }

    public void setSaxon(boolean saxon) {
        this.saxon = saxon;
    }

}