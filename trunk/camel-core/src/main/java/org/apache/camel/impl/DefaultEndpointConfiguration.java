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
package org.apache.camel.impl;

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.camel.Component;
import org.apache.camel.EndpointConfiguration;
import org.apache.camel.RuntimeCamelException;

/**
 * Default implementation of {@link EndpointConfiguration}.
 *
 * @version 
 */
public abstract class DefaultEndpointConfiguration implements EndpointConfiguration {

    private Component component;
    private URI uri;
    
    public DefaultEndpointConfiguration(Component component) {
        this.component = component;
    }

    public DefaultEndpointConfiguration(Component component, String uri) {
        this(component);
        try {
            setURI(new URI(uri));
        } catch (URISyntaxException e) {
            throw new RuntimeCamelException(e);
        }
    }

    @Override
    public URI getURI() {
        return uri;
    }

    public void setURI(URI uri) {
        this.uri = uri;
        parseURI();
    }

    public void setComponent(Component component) {
        this.component = component;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getParameter(String name) {
        return (T)ConfigurationHelper.getConfigurationParameter(this, name);
    }

    @Override
    public <T> void setParameter(String name, T value) {
        ConfigurationHelper.setConfigurationField(this, name, value);
    }

    @Override
    public String toUriString(UriFormat format) {
        return ConfigurationHelper.formatConfigurationUri(this, format);
    }

    protected Component getComponent() {
        return component;
    }

    protected void parseURI() {
        ConfigurationHelper.populateFromURI(this, new ConfigurationHelper.FieldParameterSetter());
    }
}
