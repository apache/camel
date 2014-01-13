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
import javax.xml.transform.Source;
import javax.xml.transform.TransformerException;

import org.apache.camel.Component;
import org.apache.camel.Exchange;
import org.apache.camel.api.management.ManagedAttribute;
import org.apache.camel.api.management.ManagedOperation;
import org.apache.camel.api.management.ManagedResource;
import org.apache.camel.builder.xml.XsltBuilder;
import org.apache.camel.impl.ProcessorEndpoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ManagedResource(description = "Managed XsltEndpoint")
public class XsltEndpoint extends ProcessorEndpoint {

    private static final Logger LOG = LoggerFactory.getLogger(XsltEndpoint.class);

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
    }

    @ManagedOperation(description = "Clears the cached XSLT stylesheet, forcing to re-load the stylesheet on next request")
    public void clearCachedStylesheet() {
        this.cacheCleared = true;
    }

    @ManagedAttribute(description = "Whether the XSLT stylesheet is cached")
    public boolean isCacheStylesheet() {
        return cacheStylesheet;
    }

    @ManagedAttribute(description = "Endpoint State")
    public String getState() {
        return getStatus().name();
    }

    @ManagedAttribute(description = "Camel ID")
    public String getCamelId() {
        return getCamelContext().getName();
    }

    @ManagedAttribute(description = "Camel ManagementName")
    public String getCamelManagementName() {
        return getCamelContext().getManagementName();
    }

    public XsltEndpoint findOrCreateEndpoint(String uri, String newResourceUri) {
        String newUri = uri.replace(resourceUri, newResourceUri);
        LOG.trace("Getting endpoint with URI: {}", newUri);
        return getCamelContext().getEndpoint(newUri, XsltEndpoint.class);
    }

    @Override
    protected void onExchange(Exchange exchange) throws Exception {

        if (!cacheStylesheet || cacheCleared) {
            loadResource(resourceUri);
        }
        super.onExchange(exchange);

    }

    /**
     * Loads the resource.
     *
     * @param resourceUri  the resource to load
     *
     * @throws TransformerException is thrown if error loading resource
     * @throws IOException is thrown if error loading resource
     */
    protected void loadResource(String resourceUri) throws TransformerException, IOException {
        LOG.trace("{} loading schema resource: {}", this, resourceUri);
        Source source = xslt.getUriResolver().resolve(resourceUri, null);
        if (source == null) {
            throw new IOException("Cannot load schema resource " + resourceUri);
        } else {
            xslt.setTransformerSource(source);
        }
        // now loaded so clear flag
        cacheCleared = false;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        loadResource(resourceUri);
    }

    @Override
    protected void doStop() throws Exception {
        super.doStop();
    }
}
