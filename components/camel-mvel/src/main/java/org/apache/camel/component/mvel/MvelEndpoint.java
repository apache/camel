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
package org.apache.camel.component.mvel;

import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.component.ResourceEndpoint;
import org.apache.camel.converter.IOConverter;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.util.ExchangeHelper;
import org.apache.camel.util.ObjectHelper;
import org.mvel2.ParserContext;
import org.mvel2.templates.CompiledTemplate;
import org.mvel2.templates.TemplateCompiler;
import org.mvel2.templates.TemplateRuntime;

/**
 * Transforms the message using a MVEL template.
 */
@UriEndpoint(firstVersion = "2.12.0", scheme = "mvel", title = "MVEL", syntax = "mvel:resourceUri", producerOnly = true, label = "transformation,script")
public class MvelEndpoint extends ResourceEndpoint {

    @UriParam
    private String encoding;
    private volatile String template;
    private volatile CompiledTemplate compiled;

    public MvelEndpoint(String uri, MvelComponent component, String resourceUri) {
        super(uri, component, resourceUri);
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

    @Override
    public ExchangePattern getExchangePattern() {
        return ExchangePattern.InOut;
    }

    @Override
    protected String createEndpointUri() {
        return "mvel:" + getResourceUri();
    }

    public String getEncoding() {
        return encoding;
    }

    /**
     * Character encoding of the resource content.
     */
    public void setEncoding(String encoding) {
        this.encoding = encoding;
    }

    @Override
    protected void onExchange(Exchange exchange) throws Exception {
        String path = getResourceUri();
        ObjectHelper.notNull(path, "resourceUri");

        String newResourceUri = exchange.getIn().getHeader(MvelConstants.MVEL_RESOURCE_URI, String.class);
        if (newResourceUri != null) {
            exchange.getIn().removeHeader(MvelConstants.MVEL_RESOURCE_URI);

            log.debug("{} set to {} creating new endpoint to handle exchange", MvelConstants.MVEL_RESOURCE_URI, newResourceUri);
            MvelEndpoint newEndpoint = findOrCreateEndpoint(getEndpointUri(), newResourceUri);
            newEndpoint.onExchange(exchange);
            return;
        }

        CompiledTemplate compiled;
        ParserContext mvelContext = ParserContext.create();
        Map<String, Object> variableMap = ExchangeHelper.createVariableMap(exchange);

        String content = exchange.getIn().getHeader(MvelConstants.MVEL_TEMPLATE, String.class);
        if (content != null) {
            // use content from header
            if (log.isDebugEnabled()) {
                log.debug("Mvel content read from header {} for endpoint {}", MvelConstants.MVEL_TEMPLATE, getEndpointUri());
            }
            // remove the header to avoid it being propagated in the routing
            exchange.getIn().removeHeader(MvelConstants.MVEL_TEMPLATE);
            compiled = TemplateCompiler.compileTemplate(content, mvelContext);
        } else {
            if (log.isDebugEnabled()) {
                log.debug("Mvel content read from resource {} with resourceUri: {} for endpoint {}", getResourceUri(), path, getEndpointUri());
            }
            // getResourceAsInputStream also considers the content cache
            Reader reader = getEncoding() != null ? new InputStreamReader(getResourceAsInputStream(), getEncoding()) : new InputStreamReader(getResourceAsInputStream());
            String template = IOConverter.toString(reader);
            if (!template.equals(this.template)) {
                this.template = template;
                this.compiled = TemplateCompiler.compileTemplate(template, mvelContext);
            }
            compiled = this.compiled;
        }

        // let mvel parse and execute the template
        log.debug("Mvel is evaluating using mvel context: {}", variableMap);
        Object result = TemplateRuntime.execute(compiled, mvelContext, variableMap);

        // now lets output the results to the exchange
        Message out = exchange.getOut();
        out.setBody(result.toString());
        out.setHeaders(exchange.getIn().getHeaders());
        out.setAttachments(exchange.getIn().getAttachments());
    }

    public MvelEndpoint findOrCreateEndpoint(String uri, String newResourceUri) {
        String newUri = uri.replace(getResourceUri(), newResourceUri);
        log.debug("Getting endpoint with URI: {}", newUri);
        return getCamelContext().getEndpoint(newUri, MvelEndpoint.class);
    }

}
