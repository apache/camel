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
package org.apache.camel.component.xslt;

import java.io.IOException;
import java.net.URL;

import javax.xml.transform.TransformerConfigurationException;

import org.apache.camel.Component;
import org.apache.camel.Exchange;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.builder.xml.XsltBuilder;
import org.apache.camel.impl.ProcessorEndpoint;
import org.apache.camel.util.ResourceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ManagedResource(description = "XSLT Endpoint")
public class XsltEndpoint extends ProcessorEndpoint {

    private static final transient Logger LOG = LoggerFactory.getLogger(XsltEndpoint.class);

    private XsltBuilder xslt;
    private String resourceUri;
    private boolean cacheStylesheet;
    private volatile boolean cacheCleared;

    public XsltEndpoint(String endpointUri, Component component, XsltBuilder xslt, String resourceUri,
            boolean cacheStylesheet) throws Exception {
        super(endpointUri, component, xslt);
        this.xslt = xslt;
        this.resourceUri = resourceUri;
        this.cacheStylesheet = cacheStylesheet;
        loadResource(xslt, resourceUri);
    }

    @ManagedOperation(description = "Clears the cached XSLT stylesheet, forcing to re-load the stylesheet on next request")
    public void clearCachedStylesheet() {
        this.cacheCleared = true;
    }

    @ManagedAttribute(description = "Whether the XSLT stylesheet is cached")
    public boolean isCacheStylesheet() {
        return cacheStylesheet;
    }

    private synchronized void loadResource(XsltBuilder xslt, String resourceUri) throws TransformerConfigurationException, IOException {
        LOG.trace("{} loading schema resource: {}", this, resourceUri);
        // prefer to use URL over InputStream as it loads better with http
        URL url = ResourceHelper.resolveMandatoryResourceAsUrl(getCamelContext().getClassResolver(), resourceUri);
        xslt.setTransformerURL(url);
        // now loaded so clear flag
        cacheCleared = false;
    }

    public XsltEndpoint findOrCreateEndpoint(String uri, String newResourceUri) {
        String newUri = uri.replace(resourceUri, newResourceUri);
        LOG.trace("Getting endpoint with URI: {}", newUri);
        return (XsltEndpoint) getCamelContext().getEndpoint(newUri);
    }
    
    @Override
    protected void onExchange(Exchange exchange) throws Exception {
        String newResourceUri = exchange.getIn().getHeader(XsltConstants.XSLT_RESOURCE_URI, String.class);
        if (newResourceUri != null) {
            exchange.getIn().removeHeader(XsltConstants.XSLT_RESOURCE_URI);

            LOG.trace("{} set to {} creating new endpoint to handle exchange", XsltConstants.XSLT_RESOURCE_URI, newResourceUri);
            XsltEndpoint newEndpoint = findOrCreateEndpoint(getEndpointUri(), newResourceUri);
            newEndpoint.onExchange(exchange);
            return;
        } else {            
            if (!cacheStylesheet || cacheCleared) {
                loadResource(xslt, resourceUri);
            }    
            super.onExchange(exchange);
        }
    }

}
