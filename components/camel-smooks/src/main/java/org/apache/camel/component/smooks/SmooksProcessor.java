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
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.xml.transform.sax.SAXSource;

import org.w3c.dom.Node;

import org.xml.sax.SAXException;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.WrappedFile;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.support.ResourceHelper;
import org.apache.camel.support.builder.OutputStreamBuilder;
import org.apache.camel.support.service.ServiceSupport;
import org.apache.camel.util.IOHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.smooks.Smooks;
import org.smooks.SmooksFactory;
import org.smooks.api.ExecutionContext;
import org.smooks.api.SmooksException;
import org.smooks.api.TypedKey;
import org.smooks.api.delivery.VisitorAppender;
import org.smooks.api.io.Sink;
import org.smooks.api.io.Source;
import org.smooks.api.resource.visitor.Visitor;
import org.smooks.engine.lookup.ExportsLookup;
import org.smooks.engine.report.HtmlReportGenerator;
import org.smooks.io.payload.Exports;
import org.smooks.io.sink.StreamSink;
import org.smooks.io.source.DOMSource;
import org.smooks.io.source.ReaderSource;
import org.smooks.io.source.StreamSource;
import org.smooks.io.source.URLSource;

/**
 * Smooks {@link Processor} for Camel.
 */
public class SmooksProcessor extends ServiceSupport implements Processor, CamelContextAware {

    public static final String SMOOKS_EXECUTION_CONTEXT = "CamelSmooksExecutionContext";

    private static final TypedKey<Exchange> EXCHANGE_TYPED_KEY = TypedKey.of();
    private static final Logger LOG = LoggerFactory.getLogger(SmooksProcessor.class);

    private SmooksFactory smooksFactory;
    private Smooks smooks;
    private String configUri;
    private String reportPath;

    private final Set<VisitorAppender> visitorAppender = new HashSet<>();
    private final Map<String, Visitor> selectorVisitorMap = new HashMap<>();
    private CamelContext camelContext;

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

    public void setCamelContext(CamelContext camelContext) {
        this.camelContext = camelContext;
    }

    public CamelContext getCamelContext() {
        return camelContext;
    }

    public void process(final Exchange exchange) {
        final ExecutionContext executionContext = smooks.createExecutionContext();
        try {
            executionContext.put(EXCHANGE_TYPED_KEY, exchange);
            Charset charset = ExchangeHelper.getCharset(exchange, false);
            if (charset != null) {
                // if provided use the came character encoding
                executionContext.setContentEncoding(charset.name());
            }
            exchange.getIn().setHeader(SMOOKS_EXECUTION_CONTEXT, executionContext);
            setupSmooksReporting(executionContext);

            final Exports exports = smooks.getApplicationContext().getRegistry().lookup(new ExportsLookup());
            if (exports.hasExports()) {
                final Sink[] sinks = exports.createSinks();
                smooks.filterSource(executionContext, getSource(exchange), sinks);
                setResultOnBody(exports, sinks, exchange);
            } else {
                final OutputStreamBuilder outputStreamBuilder = OutputStreamBuilder.withExchange(exchange);
                smooks.filterSource(executionContext, getSource(exchange),
                        new StreamSink<>(outputStreamBuilder));
                exchange.getMessage().setBody(outputStreamBuilder.build());
            }
        } catch (IOException e) {
            throw new RuntimeCamelException(e);
        } finally {
            executionContext.remove(EXCHANGE_TYPED_KEY);
        }
    }

    protected void setResultOnBody(final Exports exports, final Sink[] sinks, final Exchange exchange) {
        final Message message = exchange.getMessage();
        final List<Object> objects = Exports.extractSinks(sinks, exports);
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
                executionContext.getContentDeliveryRuntime().addExecutionEventListener(
                        new HtmlReportGenerator(reportPath, executionContext.getApplicationContext()));
            } catch (final IOException e) {
                LOG.warn("Cannot generate Smooks Report. The reportPath specified was [" + reportPath
                         + "]. This exception is ignored.",
                        e);
            }
        }
    }

    private Source getSource(final Exchange exchange) {
        Object payload = exchange.getIn().getBody();

        if (payload instanceof SAXSource) {
            return new ReaderSource<>((Reader) ((SAXSource) payload).getXMLReader());
        }

        if (payload instanceof Source) {
            return (Source) payload;
        }

        if (payload instanceof Node) {
            return new DOMSource((Node) payload);
        }

        if (payload instanceof InputStream) {
            return new StreamSource<>((InputStream) payload);
        }

        if (payload instanceof Reader) {
            return new ReaderSource<>((Reader) payload);
        }

        if (payload instanceof WrappedFile) {
            String systemId
                    = new javax.xml.transform.stream.StreamSource((File) exchange.getIn().getBody(WrappedFile.class).getFile())
                            .getSystemId();
            try {
                return new URLSource(new URL(systemId));
            } catch (MalformedURLException e) {
                throw new SmooksException(e);
            }
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
     * @param  visitor        The visitor implementation.
     * @param  targetSelector The message fragment target selector.
     * @return                This instance.
     */
    public SmooksProcessor addVisitor(Visitor visitor, String targetSelector) {
        selectorVisitorMap.put(targetSelector, visitor);
        return this;
    }

    /**
     * Add a visitor instance to <code>this</code> Smooks instance via a {@link VisitorAppender}.
     *
     * @param  appender The visitor appender.
     * @return          This instance.
     */
    public SmooksProcessor addVisitor(VisitorAppender appender) {
        visitorAppender.add(appender);
        return this;
    }

    public void setReportPath(String reportPath) {
        this.reportPath = reportPath;
    }

    public SmooksFactory getSmooksFactory() {
        return smooksFactory;
    }

    public void setSmooksFactory(SmooksFactory smooksFactory) {
        this.smooksFactory = smooksFactory;
    }

    private Smooks createSmooks() {
        return smooksFactory != null ? smooksFactory.createInstance() : new Smooks();
    }

    private void addAppender(Smooks smooks, Set<VisitorAppender> visitorAppenders) {
        for (VisitorAppender appender : visitorAppenders)
            smooks.addVisitors(appender);
    }

    private void addVisitor(Smooks smooks, Map<String, Visitor> selectorVisitorMap) {
        for (Entry<String, Visitor> entry : selectorVisitorMap.entrySet())
            smooks.addVisitor(entry.getValue(), entry.getKey());
    }

    @Override
    protected void doStart() throws Exception {
        try {
            if (smooks == null) {
                smooks = createSmooks();
                if (configUri != null) {
                    InputStream is = ResourceHelper.resolveMandatoryResourceAsInputStream(camelContext, configUri);
                    smooks.addResourceConfigs(is);
                }
                smooks.getApplicationContext().getRegistry().registerObject(CamelContext.class, camelContext);
            }

            addAppender(smooks, visitorAppender);
            addVisitor(smooks, selectorVisitorMap);

        } catch (Exception e) {
            throw new SmooksException(e.getMessage(), e);
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (smooks != null) {
            IOHelper.close(smooks);
            smooks = null;
        }
    }

    @Override
    public String toString() {
        return "SmooksProcessor[configUri=" + configUri + "]";
    }

}
