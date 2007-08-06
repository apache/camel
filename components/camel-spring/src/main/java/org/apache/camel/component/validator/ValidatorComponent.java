/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.validator;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.impl.ProcessorEndpoint;
import org.apache.camel.component.validator.SpringValidator;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.io.ResourceLoader;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;

import java.util.Map;

/**
 * The <a href="http://activemq.apache.org/camel/validator.html">Validator Component</a>
 * for validating XML against some schema
 *
 * @version $Revision: 1.1 $
 */
public class ValidatorComponent extends DefaultComponent<Exchange> {
    private static final transient Log log = LogFactory.getLog(ValidatorComponent.class);

    private ResourceLoader resourceLoader = new DefaultResourceLoader();

    public ResourceLoader getResourceLoader() {
        return resourceLoader;
    }

    public void setResourceLoader(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    protected Endpoint<Exchange> createEndpoint(String uri, String remaining, Map parameters) throws Exception {
        SpringValidator validator = new SpringValidator();
        Resource resource = resolveMandatoryResource(remaining);
        validator.setSchemaResource(resource);
        if (log.isDebugEnabled()) {
            log.debug(this + " using schema resource: " + resource);
        }
        return new ProcessorEndpoint(uri, this, validator);
    }

    protected Resource resolveMandatoryResource(String uri) {
        Resource resource = getResourceLoader().getResource(uri);
        if (resource == null) {
            throw new IllegalArgumentException("Could not find resource for URI: " + uri + " using: " + getResourceLoader());
        }
        else {
            return resource;
        }

    }
}
