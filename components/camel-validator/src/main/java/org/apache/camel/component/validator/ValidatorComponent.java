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
package org.apache.camel.component.validator;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.support.DefaultComponent;

/**
 * The <a href="http://camel.apache.org/validation.html">Validator Component</a> is for validating XML against a schema
 */
@org.apache.camel.spi.annotations.Component("validator")
public class ValidatorComponent extends DefaultComponent {

    @Metadata(label = "advanced", description = "To use a custom LSResourceResolver which depends on a dynamic endpoint resource URI")
    private ValidatorResourceResolverFactory resourceResolverFactory;
    
    public ValidatorComponent() {
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        ValidatorEndpoint endpoint = new ValidatorEndpoint(uri, this, remaining);
        // lookup custom resolver to use
        ValidatorResourceResolverFactory resolverFactory = resolveAndRemoveReferenceParameter(parameters, "resourceResolverFactory", ValidatorResourceResolverFactory.class);
        if (resolverFactory == null) {
            // not in endpoint then use component specific resource resolver factory
            resolverFactory = getResourceResolverFactory();
        }
        if (resolverFactory == null) {
            // fallback to use a Camel default resource resolver factory
            resolverFactory = new DefaultValidatorResourceResolverFactory();
        }
        endpoint.setResourceResolverFactory(resolverFactory);
        setProperties(endpoint, parameters);
        return endpoint;
    }

    public ValidatorResourceResolverFactory getResourceResolverFactory() {
        return resourceResolverFactory;
    }

    /**
     * To use a custom LSResourceResolver which depends on a dynamic endpoint resource URI
     */
    public void setResourceResolverFactory(ValidatorResourceResolverFactory resourceResolverFactory) {
        this.resourceResolverFactory = resourceResolverFactory;
    }

}
