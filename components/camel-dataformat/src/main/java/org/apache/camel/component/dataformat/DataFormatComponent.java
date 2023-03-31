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
package org.apache.camel.component.dataformat;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.PropertyConfigurer;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.PluginHelper;
import org.apache.camel.support.PropertyBindingSupport;
import org.apache.camel.util.StringHelper;

/**
 * The <a href="http://camel.apache.org/dataformat-component.html">Data Format Component</a> enables using
 * <a href="https://camel.apache.org/data-format.html">Data Format</a> as a component.
 */
@Component("dataformat")
public class DataFormatComponent extends DefaultComponent {

    public DataFormatComponent() {
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        String operation = StringHelper.after(remaining, ":");
        if (!"marshal".equals(operation) && !"unmarshal".equals(operation)) {
            throw new IllegalArgumentException("Operation must be either marshal or unmarshal, was: " + operation);
        }

        // create new data format as it is configured from the given parameters
        String name = StringHelper.before(remaining, ":");
        DataFormat df = getCamelContext().createDataFormat(name);
        if (df == null) {
            // if not, try to lookup existing data format
            df = getCamelContext().resolveDataFormat(name);
        }
        if (df == null) {
            throw new IllegalArgumentException("Cannot find data format with name: " + name);
        }

        // find configurer if any
        PropertyConfigurer configurer = PluginHelper.getConfigurerResolver(getCamelContext())
                .resolvePropertyConfigurer(name + "-dataformat", getCamelContext());
        // bind properties to data format
        PropertyBindingSupport.Builder builder = new PropertyBindingSupport.Builder();
        builder.withConfigurer(configurer);
        builder.bind(getCamelContext(), df, parameters);

        // create endpoint
        DataFormatEndpoint endpoint = new DataFormatEndpoint(uri, this, df);
        endpoint.setOperation(operation);
        setProperties(endpoint, parameters);
        return endpoint;
    }
}
