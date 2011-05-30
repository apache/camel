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

import org.apache.camel.impl.DefaultComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

/**
 * A useful base class for components which depend on a resource
 * such as things like Velocity or XQuery based components.
 *
 * @version 
 */
public abstract class ResourceBasedComponent extends DefaultComponent {
    protected final transient Logger log = LoggerFactory.getLogger(getClass());
    private ResourceLoader resourceLoader;

    public ResourceLoader getResourceLoader() {
        if (resourceLoader == null) {
            resourceLoader = new CamelResourceLoader(getCamelContext());
        }
        return resourceLoader;
    }

    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    protected Resource resolveMandatoryResource(String uri) {
        Resource resource = getResourceLoader().getResource(uri);
        if (resource == null) {
            throw new IllegalArgumentException("Could not find resource for URI: " + uri + " using: " + getResourceLoader());
        } else {
            return resource;
        }
    }

}
