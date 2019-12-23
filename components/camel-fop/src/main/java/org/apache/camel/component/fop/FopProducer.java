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
package org.apache.camel.component.fop;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.sax.SAXResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.camel.Exchange;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.support.PropertyBindingSupport;
import org.apache.camel.util.PropertiesHelper;
import org.apache.fop.apps.FOPException;
import org.apache.fop.apps.FOUserAgent;
import org.apache.fop.apps.Fop;
import org.apache.fop.apps.FopFactory;
import org.apache.fop.pdf.PDFEncryptionParams;

/**
 * The Fop producer.
 */
public class FopProducer extends DefaultProducer {
    private final FopFactory fopFactory;
    private final String outputFormat;

    public FopProducer(FopEndpoint endpoint, FopFactory fopFactory, String outputFormat) {
        super(endpoint);
        this.fopFactory = fopFactory;
        this.outputFormat = outputFormat;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        FOUserAgent userAgent = fopFactory.newFOUserAgent();
        Map<String, Object> headers = exchange.getIn().getHeaders();
        setRenderParameters(userAgent, headers);
        setEncryptionParameters(userAgent, headers);
        setUserAgentRendererOptions(userAgent, headers);

        String outputFormat = getOutputFormat(exchange);
        Source src = exchange.getIn().getBody(StreamSource.class);

        OutputStream out = transform(userAgent, outputFormat, src);
        exchange.getOut().setBody(out);

        // propagate headers
        exchange.getOut().setHeaders(headers);
    }

    private String getOutputFormat(Exchange exchange) {
        String headerOutputFormat = exchange.getIn().getHeader(FopConstants.CAMEL_FOP_OUTPUT_FORMAT, String.class);
        if (headerOutputFormat != null) {
            // it may be a short hand
            FopOutputType type = exchange.getContext().getTypeConverter().tryConvertTo(FopOutputType.class, exchange, headerOutputFormat);
            if (type != null) {
                return type.getFormatExtended();
            } else {
                return headerOutputFormat;
            }
        } else {
            return outputFormat;
        }
    }

    private OutputStream transform(FOUserAgent userAgent, String outputFormat, Source src)
        throws FOPException, TransformerException {
        OutputStream out = new ByteArrayOutputStream();
        Fop fop = fopFactory.newFop(outputFormat, userAgent, out);
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        transformerFactory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, Boolean.TRUE);
        Transformer transformer = transformerFactory.newTransformer();

        Result res = new SAXResult(fop.getDefaultHandler());
        transformer.transform(src, res);
        return out;
    }

    @SuppressWarnings("unchecked")
    private void setEncryptionParameters(FOUserAgent userAgent, Map<String, Object> headers) {
        Map<String, Object> encryptionParameters = PropertiesHelper
            .extractProperties(headers, FopConstants.CAMEL_FOP_ENCRYPT);
        if (!encryptionParameters.isEmpty()) {
            PDFEncryptionParams encryptionParams = new PDFEncryptionParams();
            PropertyBindingSupport.bindProperties(getEndpoint().getCamelContext(), encryptionParams, encryptionParameters);
            userAgent.getRendererOptions().put("encryption-params", encryptionParams);
        }
    }

    private void setUserAgentRendererOptions(FOUserAgent userAgent, Map<String, Object> headers) {
        Map<String, Object> parameters = PropertiesHelper.extractProperties(headers, FopConstants.CAMEL_FOP_RENDERER_OPTIONS);
        if (!parameters.isEmpty()) {
            userAgent.getRendererOptions().putAll(parameters);
        }
    }

    private void setRenderParameters(FOUserAgent userAgent, Map<String, Object> headers) {
        Map<String, Object> parameters = PropertiesHelper.extractProperties(headers, FopConstants.CAMEL_FOP_RENDER);
        if (!parameters.isEmpty()) {
            PropertyBindingSupport.bindProperties(getEndpoint().getCamelContext(), userAgent, parameters);
        }
    }
    
}
