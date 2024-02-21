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

import java.io.InputStream;
import java.util.Map;

import com.bazaarvoice.jolt.Chainr;
import com.bazaarvoice.jolt.ContextualTransform;
import com.bazaarvoice.jolt.Defaultr;
import com.bazaarvoice.jolt.JoltTransform;
import com.bazaarvoice.jolt.JsonUtils;
import com.bazaarvoice.jolt.Removr;
import com.bazaarvoice.jolt.Shiftr;
import com.bazaarvoice.jolt.Sortr;
import com.bazaarvoice.jolt.Transform;
import org.apache.camel.Category;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
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
    public ExchangePattern getExchangePattern() {
        return ExchangePattern.InOut;
    }

    @Override
    protected String createEndpointUri() {
        return "jolt:" + getResourceUri();
    }

    private synchronized JoltTransform getTransform() throws Exception {
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
     * Specifies if the input is hydrated JSON or a JSON String.
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
            input = exchange.getIn().getBody();
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

}
