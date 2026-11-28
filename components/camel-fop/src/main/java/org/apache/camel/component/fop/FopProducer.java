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
import java.util.Date;
import java.util.Locale;
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
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.PropertiesHelper;
import org.apache.fop.apps.FOPException;
import org.apache.fop.apps.FOUserAgent;
import org.apache.fop.apps.Fop;
import org.apache.fop.apps.FopFactory;
import org.apache.fop.pdf.PDFEncryptionParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Fop producer.
 */
public class FopProducer extends DefaultProducer {
    private static final Logger LOG = LoggerFactory.getLogger(FopProducer.class);

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
        setRenderParameters(exchange, userAgent, headers);
        setEncryptionParameters(exchange, userAgent, headers);
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
            // it may be a shorthand
            FopOutputType type
                    = exchange.getContext().getTypeConverter().tryConvertTo(FopOutputType.class, exchange, headerOutputFormat);
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
        // align with Camel's standard secure XML processing: do not allow access to external DTD/stylesheet
        try {
            transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        } catch (Exception e) {
            // ignore if the factory does not support the attribute
        }
        try {
            transformerFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET, "");
        } catch (Exception e) {
            // ignore if the factory does not support the attribute
        }
        Transformer transformer = transformerFactory.newTransformer();

        Result res = new SAXResult(fop.getDefaultHandler());
        transformer.transform(src, res);
        return out;
    }

    private static void setEncryptionParameters(Exchange exchange, FOUserAgent userAgent, Map<String, Object> headers)
            throws NoTypeConversionAvailableException {
        Map<String, Object> parameters = PropertiesHelper.extractProperties(headers, FopConstants.CAMEL_FOP_ENCRYPT);
        if (parameters.isEmpty()) {
            return;
        }
        PDFEncryptionParams encryptionParams = new PDFEncryptionParams();
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            String name = entry.getKey();
            Object value = entry.getValue();
            if (value == null) {
                continue;
            }
            // the values are applied as given, only converted to the type of the setter
            switch (name.toLowerCase(Locale.ROOT)) {
                case "userpassword" -> encryptionParams.setUserPassword(convertTo(exchange, String.class, value));
                case "ownerpassword" -> encryptionParams.setOwnerPassword(convertTo(exchange, String.class, value));
                case "allowprint" -> encryptionParams.setAllowPrint(convertTo(exchange, Boolean.class, value));
                case "allowcopycontent" -> encryptionParams.setAllowCopyContent(convertTo(exchange, Boolean.class, value));
                case "alloweditcontent" -> encryptionParams.setAllowEditContent(convertTo(exchange, Boolean.class, value));
                case "alloweditannotations" ->
                    encryptionParams.setAllowEditAnnotations(convertTo(exchange, Boolean.class, value));
                case "allowfillinforms" -> encryptionParams.setAllowFillInForms(convertTo(exchange, Boolean.class, value));
                case "allowaccesscontent" ->
                    encryptionParams.setAllowAccessContent(convertTo(exchange, Boolean.class, value));
                case "allowassembledocument" ->
                    encryptionParams.setAllowAssembleDocument(convertTo(exchange, Boolean.class, value));
                case "allowprinthq" -> encryptionParams.setAllowPrintHq(convertTo(exchange, Boolean.class, value));
                case "encryptmetadata" -> encryptionParams.setEncryptMetadata(convertTo(exchange, Boolean.class, value));
                case "encryptionlengthinbits" ->
                    encryptionParams.setEncryptionLengthInBits(convertTo(exchange, Integer.class, value));
                default -> logUnsupported(FopConstants.CAMEL_FOP_ENCRYPT, name);
            }
        }
        userAgent.getRendererOptions().put("encryption-params", encryptionParams);
    }

    private void setUserAgentRendererOptions(FOUserAgent userAgent, Map<String, Object> headers) {
        Map<String, Object> parameters = PropertiesHelper.extractProperties(headers, FopConstants.CAMEL_FOP_RENDERER_OPTIONS);
        if (!parameters.isEmpty()) {
            userAgent.getRendererOptions().putAll(parameters);
        }
    }

    private static void setRenderParameters(Exchange exchange, FOUserAgent userAgent, Map<String, Object> headers)
            throws NoTypeConversionAvailableException {
        Map<String, Object> parameters = PropertiesHelper.extractProperties(headers, FopConstants.CAMEL_FOP_RENDER);
        for (Map.Entry<String, Object> entry : parameters.entrySet()) {
            String name = entry.getKey();
            Object value = entry.getValue();
            if (value == null) {
                continue;
            }
            // the values are applied as given, only converted to the type of the setter
            switch (name.toLowerCase(Locale.ROOT)) {
                case "producer" -> userAgent.setProducer(convertTo(exchange, String.class, value));
                case "creator" -> userAgent.setCreator(convertTo(exchange, String.class, value));
                case "creationdate" -> userAgent.setCreationDate(convertTo(exchange, Date.class, value));
                case "author" -> userAgent.setAuthor(convertTo(exchange, String.class, value));
                case "title" -> userAgent.setTitle(convertTo(exchange, String.class, value));
                case "subject" -> userAgent.setSubject(convertTo(exchange, String.class, value));
                case "keywords" -> userAgent.setKeywords(convertTo(exchange, String.class, value));
                case "targetresolution" -> userAgent.setTargetResolution(convertTo(exchange, Float.class, value));
                case "accessibility" -> userAgent.setAccessibility(convertTo(exchange, Boolean.class, value));
                case "pdfuaenabled" -> userAgent.setPdfUAEnabled(convertTo(exchange, Boolean.class, value));
                case "keepemptytags" -> userAgent.setKeepEmptyTags(convertTo(exchange, Boolean.class, value));
                case "locatorenabled" -> userAgent.setLocatorEnabled(convertTo(exchange, Boolean.class, value));
                case "conservememorypolicy" -> userAgent.setConserveMemoryPolicy(convertTo(exchange, Boolean.class, value));
                default -> logUnsupported(FopConstants.CAMEL_FOP_RENDER, name);
            }
        }
    }

    private static <T> T convertTo(Exchange exchange, Class<T> type, Object value)
            throws NoTypeConversionAvailableException {
        return exchange.getContext().getTypeConverter().mandatoryConvertTo(type, exchange, value);
    }

    private static void logUnsupported(String prefix, String name) {
        LOG.warn("Ignoring header {}{} as it is not a supported option", prefix, name);
    }

}
