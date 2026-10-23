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
package org.apache.camel.component.jolt;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.Map;

import io.joltcommunity.jolt.Chainr;
import io.joltcommunity.jolt.ContextualTransform;
import io.joltcommunity.jolt.Defaultr;
import io.joltcommunity.jolt.JoltTransform;
import io.joltcommunity.jolt.JsonUtils;
import io.joltcommunity.jolt.Shiftr;
import io.joltcommunity.jolt.Sortr;
import io.joltcommunity.jolt.Transform;
import io.joltcommunity.jolt.removr.Removr;
import org.apache.camel.Category;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.StreamCache;
import org.apache.camel.WrappedFile;
import org.apache.camel.component.ResourceEndpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.util.ObjectHelper;

/**
 * JSON to JSON transformation using JOLT.
 */
@UriEndpoint(firstVersion = "2.16.0", scheme = "jolt", title = "JOLT", syntax = "jolt:resourceUri", producerOnly = true,
             remote = false, category = { Category.TRANSFORMATION }, headersClass = JoltConstants.class)
@Metadata(excludeProperties = "allowContextMapAll")
public class JoltEndpoint extends ResourceEndpoint {

    private JoltTransform transform;

    @UriParam(defaultValue = "Hydrated")
    private JoltInputOutputType outputType;

    @UriParam(defaultValue = "Hydrated")
    private JoltInputOutputType inputType;

    @UriParam(defaultValue = "Chainr")
    private JoltTransformType transformDsl = JoltTransformType.Chainr;

    @UriParam(defaultValue = "false")
    private boolean allowTemplateFromHeader;

    public JoltEndpoint() {
    }

    public JoltEndpoint(String uri, JoltComponent component, String resourceUri) {
        super(uri, component, resourceUri);
    }

    @Override
    public boolean isRemote() {
        return false;
    }

    @Override
    public void clearContentCache() {
        super.clearContentCache();
        transform = null;
    }

    @Override
    public ExchangePattern getExchangePattern() {
        return ExchangePattern.InOut;
    }

    @Override
    protected String createEndpointUri() {
        return "jolt:" + getResourceUri();
    }

    private JoltTransform getTransform() throws Exception {
        getInternalLock().lock();
        try {
            if (transform == null) {
                if (log.isDebugEnabled()) {
                    String path = getResourceUri();
                    log.debug("Jolt content read from resource {} with resourceUri: {} for endpoint {}", getResourceUri(), path,
                            getEndpointUri());
                }

                // Sortr does not require a spec
                if (this.transformDsl == JoltTransformType.Sortr) {
                    this.transform = new Sortr();
                } else {
                    // getResourceAsInputStream also considers the content cache
                    Object spec = JsonUtils.jsonToObject(getResourceAsInputStream());
                    switch (this.transformDsl) {
                        case Shiftr:
                            this.transform = new Shiftr(spec);
                            break;
                        case Defaultr:
                            this.transform = new Defaultr(spec);
                            break;
                        case Removr:
                            this.transform = new Removr(spec);
                            break;
                        case Chainr:
                        default:
                            this.transform = Chainr.fromSpec(spec);
                            break;
                    }
                }

            }
            return transform;
        } finally {
            getInternalLock().unlock();
        }
    }

    /**
     * Sets the Transform to use. If not set a Transform specified by the transformDsl will be created
     */
    public void setTransform(JoltTransform transform) {
        this.transform = transform;
    }

    public JoltInputOutputType getOutputType() {
        return outputType;
    }

    /**
     * Specifies if the output should be hydrated JSON or a JSON String.
     */
    public void setOutputType(JoltInputOutputType outputType) {
        this.outputType = outputType;
    }

    public JoltInputOutputType getInputType() {
        return inputType;
    }

    /**
     * Specifies if the input is hydrated JSON or a JSON String. With Hydrated, a body that is JSON text (its first
     * non-whitespace character is { or [), bytes, a stream or a file is parsed as JSON, and any other body, such as a
     * Map or a List, is used as it is. With JsonString, the body is always parsed as JSON.
     */
    public void setInputType(JoltInputOutputType inputType) {
        this.inputType = inputType;
    }

    public JoltTransformType getTransformDsl() {
        return transformDsl;
    }

    /**
     * Specifies the Transform DSL of the endpoint resource. If none is specified <code>Chainr</code> will be used.
     */
    public void setTransformDsl(JoltTransformType transformType) {
        this.transformDsl = transformType;
    }

    public boolean isAllowTemplateFromHeader() {
        return allowTemplateFromHeader;
    }

    /**
     * Whether to allow to use resource template from header or not (default false).
     *
     * Enabling this allows to specify dynamic templates via message header. However this can be seen as a potential
     * security vulnerability if the header is coming from a malicious user, so use this with care.
     */
    public void setAllowTemplateFromHeader(boolean allowTemplateFromHeader) {
        this.allowTemplateFromHeader = allowTemplateFromHeader;
    }

    public JoltEndpoint findOrCreateEndpoint(String uri, String newResourceUri) {
        String newUri = uri.replace(getResourceUri(), newResourceUri);
        log.debug("Getting endpoint with URI: {}", newUri);
        return getCamelContext().getEndpoint(newUri, JoltEndpoint.class);
    }

    @Override
    protected void onExchange(Exchange exchange) throws Exception {
        String path = getResourceUri();
        ObjectHelper.notNull(path, "resourceUri");

        String newResourceUri = null;
        if (allowTemplateFromHeader) {
            newResourceUri = exchange.getIn().getHeader(JoltConstants.JOLT_RESOURCE_URI, String.class);
        }
        if (newResourceUri != null) {
            exchange.getIn().removeHeader(JoltConstants.JOLT_RESOURCE_URI);
            log.debug("{} set to {} creating new endpoint to handle exchange", JoltConstants.JOLT_RESOURCE_URI, newResourceUri);
            JoltEndpoint newEndpoint = findOrCreateEndpoint(getEndpointUri(), newResourceUri);
            newEndpoint.onExchange(exchange);
            return;
        }

        Object input;
        if (getInputType() == JoltInputOutputType.JsonString) {
            input = JsonUtils.jsonToObject(exchange.getIn().getBody(InputStream.class));
        } else {
            input = hydratedInput(exchange);
        }

        Map<String, Object> inputContextMap = null;
        if (allowTemplateFromHeader) {
            inputContextMap = exchange.getIn().getHeader(JoltConstants.JOLT_CONTEXT, Map.class);
        }
        Object output;
        if (inputContextMap != null) {
            output = ((ContextualTransform) getTransform()).transform(input, inputContextMap);
        } else {
            output = ((Transform) getTransform()).transform(input);
        }

        // now lets output the results to the exchange
        Object body = output;
        if (getOutputType() == JoltInputOutputType.JsonString) {
            body = JsonUtils.toJsonString(output);
        }
        ExchangeHelper.setInOutBodyPatternAware(exchange, body);
    }

    /**
     * The input for the Hydrated input type: JSON text, bytes, a stream or a file is parsed, any other body (such as a
     * Map or a List) is used as it is.
     */
    private static Object hydratedInput(Exchange exchange) throws Exception {
        Object body = exchange.getIn().getBody();
        if (body instanceof CharSequence text) {
            return isJsonText(text) ? JsonUtils.jsonToObject(text.toString()) : body;
        }
        if (body instanceof byte[] || body instanceof InputStream || body instanceof StreamCache
                || body instanceof File || body instanceof Path || body instanceof WrappedFile) {
            if (ExchangeHelper.getCharsetName(exchange, false) != null) {
                // decode the text in the charset of the exchange
                return JsonUtils.jsonToObject(exchange.getIn().getMandatoryBody(String.class));
            }
            // let the JSON parser detect the encoding
            return JsonUtils.jsonToObject(exchange.getIn().getMandatoryBody(InputStream.class));
        }
        return body;
    }

    /**
     * Whether the text is a JSON object or array, that is whether its first non-whitespace character is { or [
     */
    private static boolean isJsonText(CharSequence text) {
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (!Character.isWhitespace(ch)) {
                return ch == '{' || ch == '[';
            }
        }
        return false;
    }

}
