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
package org.apache.camel.component.qute;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.util.Map;
import java.util.Optional;

import io.quarkus.qute.Engine;
import io.quarkus.qute.EngineBuilder;
import io.quarkus.qute.ReflectionValueResolver;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import io.quarkus.qute.TemplateLocator.TemplateLocation;
import io.quarkus.qute.Variant;
import org.apache.camel.Exchange;
import org.apache.camel.component.ResourceEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * Transforms the message using a Quarkus Qute template.
 */
@UriEndpoint(firstVersion = "3.2.0", scheme = "qute", title = "Qute", syntax = "qute:resourceUri", producerOnly = true, label = "transformation")
public class QuteEndpoint extends ResourceEndpoint {
    private Engine quarkusEngine;

    @UriParam
    private String encoding;

    public QuteEndpoint() {
    }

    public QuteEndpoint(String uri, QuteComponent component, String resourceUri) {
        super(uri, component, resourceUri);
    }

    @Override
    protected String createEndpointUri() {
        return "qute:" + getResourceUri();
    }

    public void setQuarkusEngine(Engine engine) {
        this.quarkusEngine = engine;
    }

    private synchronized Engine getQuarkusEngine() {
        if (quarkusEngine == null) {
            EngineBuilder builder = Engine.builder().addDefaults();
            builder.addValueResolver(ReflectionValueResolver::new);
            builder.addLocator(this::locate);

            quarkusEngine = builder.build();
        }
        return quarkusEngine;
    }

    private Optional<TemplateLocation> locate(String path) {
        return Optional.of(new TemplateLocation() {
            private URL locatePath(String path) {
                ClassLoader cl = Thread.currentThread().getContextClassLoader();
                if (cl == null) {
                    cl = QuteEndpoint.class.getClassLoader();
                }

                return cl.getResource(path);
            }

            @Override
            public Reader read() {
                try {
                    InputStream in;
                    if (path.equals(getResourceUri())) {
                        in = getResourceAsInputStream();
                    } else {
                        in = locatePath(path).openStream();
                    }

                    Reader reader = getEncoding() != null ? new InputStreamReader(in, getEncoding()) : new InputStreamReader(in);
                    return reader;
                } catch (Exception e) {
                    log.warn("can not load template " + path + " due to " + e);
                    return null;
                }
            }

            @Override
            public Optional<Variant> getVariant() {
                return Optional.empty();
            }
        });
    }

    /**
     * Character encoding of the resource content.
     */
    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    public String getEncoding() {
        return encoding;
    }

    public QuteEndpoint findOrCreateEndpoint(String uri, String newResourceUri) {
        String newUri = uri.replace(getResourceUri(), newResourceUri);
        log.debug("Getting endpoint with URI: {}", newUri);
        return getCamelContext().getEndpoint(newUri, QuteEndpoint.class);
    }

    @Override
    protected void onExchange(Exchange exchange) throws Exception {
        String path = getResourceUri();
        ObjectHelper.notNull(path, "resourceUri");

        String newResourceUri = exchange.getIn().getHeader(QuteConstants.QUTE_RESOURCE_URI, String.class);
        if (newResourceUri != null) {
            exchange.getIn().removeHeader(QuteConstants.QUTE_RESOURCE_URI);

            log.debug("{} set to {} creating new endpoint to handle exchange", QuteConstants.QUTE_RESOURCE_URI, newResourceUri);
            QuteEndpoint newEndpoint = findOrCreateEndpoint(getEndpointUri(), newResourceUri);
            newEndpoint.onExchange(exchange);
            return;
        }

        String content = exchange.getIn().getHeader(QuteConstants.QUTE_TEMPLATE, String.class);
        if (content != null) {
            // remove the header to avoid it being propagated in the routing
            exchange.getIn().removeHeader(QuteConstants.QUTE_TEMPLATE);
        }

        TemplateInstance instance = exchange.getIn().getHeader(QuteConstants.QUTE_TEMPLATE_INSTANCE, TemplateInstance.class);
        if (instance != null) {
            // use template instance from header
            if (log.isDebugEnabled()) {
                log.debug("Qute template instance is from header {} for endpoint {}", QuteConstants.QUTE_TEMPLATE_INSTANCE, getEndpointUri());
            }
            exchange.getIn().removeHeader(QuteConstants.QUTE_TEMPLATE_INSTANCE);
        } else {
            Template template;
            if (content == null) {
                quarkusEngine = getQuarkusEngine();
                template = quarkusEngine.getTemplate(path);
            } else {
                // This is the first time to parse the content
                template = quarkusEngine.parse(content);
            }
            instance = template.instance();
        }

        Map<String, Object> map = exchange.getIn().getHeader(QuteConstants.QUTE_TEMPLATE_DATA, Map.class);
        if (map != null) {
            map.forEach(instance::data);
        }

        ExchangeHelper.createVariableMap(exchange).forEach(instance::data);
        exchange.getMessage().setBody(instance.render().trim());
    }
}
