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
package org.apache.camel.component.context;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.spi.Metadata;
import org.apache.camel.util.ObjectHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Camel Component which exposes a local {@link CamelContext} instance as a black box set of endpoints.
 */
public class LocalContextComponent extends DefaultComponent {
    private static final Logger LOG = LoggerFactory.getLogger(LocalContextComponent.class);

    private CamelContext localCamelContext;
    @Metadata(label = "advanced", defaultValue = "direct,seda,mock")
    private List<String> localProtocolSchemes = new ArrayList<String>(Arrays.asList("direct", "seda", "mock"));

    public LocalContextComponent(CamelContext localCamelContext) {
        ObjectHelper.notNull(localCamelContext, "localCamelContext");
        this.localCamelContext = localCamelContext;
    }

    public List<String> getLocalProtocolSchemes() {
        return localProtocolSchemes;
    }

    /**
     * Sets the list of protocols which are used to expose public endpoints by default
     */
    public void setLocalProtocolSchemes(List<String> localProtocolSchemes) {
        this.localProtocolSchemes = localProtocolSchemes;
    }

    public CamelContext getLocalCamelContext() {
        return localCamelContext;
    }

    /**
     * Sets the local CamelContext to use.
     */
    public void setLocalCamelContext(CamelContext localCamelContext) {
        this.localCamelContext = localCamelContext;
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters)
        throws Exception {

        // first check if we are using a fully qualified name: [context:]contextId:endpointUri
        Map<String, Endpoint> map = getLocalCamelContext().getEndpointMap();

        if (LOG.isTraceEnabled()) {
            LOG.trace("Trying to lookup {} in local map {}", remaining, map.keySet());
        }
        Endpoint endpoint = map.get(remaining);
        if (endpoint != null) {
            logUsingEndpoint(uri, endpoint);
            return new ContextEndpoint(uri, this, endpoint);
            //return new ExportedEndpoint(endpoint);
        }

        // look to see if there is an endpoint of name 'remaining' using one of the local endpoints within
        // the black box CamelContext
        String[] separators = {":", "://"};
        for (String scheme : localProtocolSchemes) {
            for (String separator : separators) {
                String newUri = scheme + separator + remaining;
                endpoint = map.get(newUri);
                if (endpoint != null) {
                    logUsingEndpoint(uri, endpoint);
                    return new ContextEndpoint(uri, this, endpoint);
                    //return new ExportedEndpoint(endpoint);
                }
            }
        }
        throw new ResolveEndpointFailedException("Cannot find the endpoint with uri " + uri + " in the CamelContext " + getLocalCamelContext().getName());
    }

    protected void logUsingEndpoint(String uri, Endpoint endpoint) {
        LOG.debug("Mapping the URI: {} to local endpoint: {}", uri, endpoint);
    }

}
