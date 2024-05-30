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
package org.apache.camel.component.smooks;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.util.*;
import java.util.Map.Entry;

import javax.xml.transform.Result;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.stream.StreamSource;

import org.w3c.dom.Node;

import org.xml.sax.SAXException;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.Service;
import org.apache.camel.WrappedFile;
import org.apache.camel.attachment.Attachment;
import org.apache.camel.attachment.AttachmentMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smooks.Smooks;
import org.smooks.SmooksFactory;
import org.smooks.api.ExecutionContext;
import org.smooks.api.SmooksException;
import org.smooks.api.TypedKey;
import org.smooks.api.delivery.VisitorAppender;
import org.smooks.api.resource.visitor.Visitor;
import org.smooks.engine.lookup.ExportsLookup;
import org.smooks.engine.report.HtmlReportGenerator;
import org.smooks.io.payload.Exports;

/**
 * Smooks {@link Processor} for Camel.
 *
 * @author Christian Mueller
 * @author Daniel Bevenius
 */
public class SmooksProcessor implements Processor, Service, CamelContextAware {
    public static final String SMOOKS_EXECUTION_CONTEXT = "CamelSmooksExecutionContext";
    public static final String CAMEL_CHARACTER_ENCODING = "CamelCharsetName";
    private static final Logger LOGGER = LoggerFactory.getLogger(SmooksProcessor.class);
    public static final TypedKey<Exchange> EXCHANGE_TYPED_KEY = TypedKey.of();

    private Smooks smooks;
    private String configUri;
    private String reportPath;

    private Set<VisitorAppender> visitorAppenders = new HashSet<>();
    private Map<String, Visitor> selectorVisitorMap = new HashMap<>();
    private CamelContext camelContext;
    private boolean attachmentsSupported = false;

    public SmooksProcessor(final CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public SmooksProcessor(final Smooks smooks, final CamelContext camelContext) {
        this(camelContext);
        this.smooks = smooks;
    }

    public SmooksProcessor(final String configUri, final CamelContext camelContext) throws IOException, SAXException {
        this(camelContext);
        this.configUri = configUri;
    }

    public void process(final Exchange exchange) {
        //forward headers
        exchange.getMessage().setHeaders(exchange.getIn().getHeaders());

        if (attachmentsSupported) {
            //forward attachments
            if (exchange.getIn(AttachmentMessage.class).hasAttachments()) {
                for (Entry<String, Attachment> attachmentObject : exchange.getIn(AttachmentMessage.class).getAttachmentObjects().entrySet()) {
                    exchange.getMessage(AttachmentMessage.class).addAttachmentObject(attachmentObject.getKey(), attachmentObject.getValue());
                }
            }
        }

        final ExecutionContext executionContext = smooks.createExecutionContext();
        executionContext.put(EXCHANGE_TYPED_KEY, exchange);
        String charsetName = (String) exchange.getProperty(CAMEL_CHARACTER_ENCODING);
        if (charsetName != null) //if provided use the came character encoding
        {
            executionContext.setContentEncoding(charsetName);
        }
        exchange.getIn().setHeader(SMOOKS_EXECUTION_CONTEXT, executionContext);
        setupSmooksReporting(executionContext);

        final Exports exports = smooks.getApplicationContext().getRegistry().lookup(new ExportsLookup());
        if (exports.hasExports()) {
            final Result[] results = exports.createResults();
            smooks.filterSource(executionContext, getSource(exchange), results);
            setResultOnBody(exports, results, exchange);
        } else {
            smooks.filterSource(executionContext, getSource(exchange));
        }

        executionContext.remove(EXCHANGE_TYPED_KEY);
    }

    protected void setResultOnBody(final Exports exports, final Result[] results, final Exchange exchange) {
        final Message message = exchange.getMessage();
        final List<Object> objects = Exports.extractResults(results, exports);
        if (objects.size() == 1) {
            Object value = objects.get(0);
            message.setBody(value);
        } else {
            message.setBody(objects);
        }
    }

    private void setupSmooksReporting(final ExecutionContext executionContext) {
        if (reportPath != null) {
            try {
                executionContext.getContentDeliveryRuntime().addExecutionEventListener(new HtmlReportGenerator(reportPath, executionContext.getApplicationContext()));
            } catch (final IOException e) {
                LOGGER.info("Could not generate Smooks Report. The reportPath specified was [" + reportPath + "].", e);
            }
        }
    }

    private Source getSource(final Exchange exchange) {
        Object payload = exchange.getIn().getBody();


        if (payload instanceof SAXSource) {
            return new StreamSource((Reader) ((SAXSource) payload).getXMLReader());
        }

        if (payload instanceof Source) {
            return (Source) payload;
        }

        if (payload instanceof Node) {
            return new DOMSource((Node) payload);
        }

        if (payload instanceof InputStream) {
            return new StreamSource((InputStream) payload);
        }

        if (payload instanceof Reader) {
            return new StreamSource((Reader) payload);
        }

        if (payload instanceof WrappedFile) {
            return new StreamSource((File) exchange.getIn().getBody(WrappedFile.class).getFile());
        }

        return exchange.getIn().getBody(Source.class);
    }

    public String getSmooksConfig() {
        return configUri;
    }

    public void setSmooksConfig(final String smooksConfig) {
        this.configUri = smooksConfig;
    }

    /**
     * Add a visitor instance.
     *
     * @param visitor        The visitor implementation.
     * @param targetSelector The message fragment target selector.
     * @return This instance.
     */
    public SmooksProcessor addVisitor(Visitor visitor, String targetSelector) {
        selectorVisitorMap.put(targetSelector, visitor);
        return this;
    }

    /**
     * Add a visitor instance to <code>this</code> Smooks instance via a
     * {@link VisitorAppender}.
     *
     * @param appender The visitor appender.
     * @return This instance.
     */
    public SmooksProcessor addVisitor(VisitorAppender appender) {
        visitorAppenders.add(appender);
        return this;
    }

    public void setReportPath(String reportPath) {
        this.reportPath = reportPath;
    }

    @Override
    public void start() {
        try {
            if (smooks == null) {
                smooks = createSmooks();
                if (configUri != null) {
                    smooks.addResourceConfigs(configUri);
                }
                smooks.getApplicationContext().getRegistry().registerObject(CamelContext.class, camelContext);
            }

            addAppenders(smooks, visitorAppenders);
            addVisitors(smooks, selectorVisitorMap);

            InputStream inputStream = null;
            try {
                inputStream = camelContext.getClassResolver().loadResourceAsStream("META-INF/services/org/apache/camel/other.properties");
                if (inputStream != null) {
                    final Properties properties = new Properties();
                    properties.load(inputStream);
                    if (properties.getProperty("name") != null && properties.getProperty("name").equals("attachments")) {
                        attachmentsSupported = true;
                    }
                }
            } finally {
                if (!attachmentsSupported) {
                    LOGGER.warn("Attachments module could not be found: attachments will not be propagated");
                }
                if (inputStream != null) {
                    inputStream.close();
                }
            }

            LOGGER.info(this + " Started");
        } catch (SAXException | IOException e) {
            throw new SmooksException(e.getMessage(), e);
        }
    }

    private Smooks createSmooks() {
        final SmooksFactory smooksFactory = (SmooksFactory) camelContext.getRegistry().lookupByName(SmooksFactory.class.getName());
        return smooksFactory != null ? smooksFactory.createInstance() : new Smooks();
    }

    private void addAppenders(Smooks smooks, Set<VisitorAppender> visitorAppenders) {
        for (VisitorAppender appender : visitorAppenders)
            smooks.addVisitors(appender);
    }

    private void addVisitors(Smooks smooks, Map<String, Visitor> selectorVisitorMap) {
        for (Entry<String, Visitor> entry : selectorVisitorMap.entrySet())
            smooks.addVisitor(entry.getValue(), entry.getKey());
    }

    public void stop() {
        if (smooks != null) {
            smooks.close();
            smooks = null;
        }
        LOGGER.info(this + " Stopped");
    }

    @Override
    public String toString() {
        return "SmooksProcessor [configUri=" + configUri + "]";
    }

    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

}
