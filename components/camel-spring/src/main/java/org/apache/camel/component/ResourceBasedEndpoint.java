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
package org.apache.camel.component;

import org.apache.camel.Component;
import org.apache.camel.Processor;
import org.apache.camel.impl.ProcessorEndpoint;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

/**
 * A useful base class for endpoints which depend on a resource
 * such as things like Velocity or XQuery based components
 *
 * @version $Revision$
 */
public abstract class ResourceBasedEndpoint extends ProcessorEndpoint {
    protected final transient Log LOG = LogFactory.getLog(getClass());
    private final String resourceUri;
    private ResourceLoader resourceLoader = new DefaultResourceLoader();
    private Resource resource;

    public ResourceBasedEndpoint(String endpointUri, Component component, String resourceUri, Processor processor) {
        super(endpointUri, component, processor);
        this.resourceUri = resourceUri;
    }

    protected ResourceBasedEndpoint(String endpointUri, Processor processor, String resourceUri) {
        super(endpointUri, processor);
        this.resourceUri = resourceUri;
    }

    public Resource getResource() {
        if (resource == null) {
            resource = getResourceLoader().getResource(resourceUri);
            if (resource == null) {
                throw new IllegalArgumentException("Could not find resource for URI: " + resourceUri + " using: " + getResourceLoader());
            }
        }
        return resource;
    }

    public ResourceLoader getResourceLoader() {
        return resourceLoader;
    }

    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }
}