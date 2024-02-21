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
package org.apache.camel.support.jsse;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import org.apache.camel.CamelContext;
import org.apache.camel.CamelContextAware;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.ClassResolver;
import org.apache.camel.spi.Resource;
import org.apache.camel.spi.ResourceLoader;
import org.apache.camel.util.ObjectHelper;

/**
 * Base class that provides optional integration with core Camel capabilities.
 */
public class JsseParameters implements CamelContextAware {

    private CamelContext context;

    /**
     * @see #setCamelContext(CamelContext)
     */
    @Override
    public CamelContext getCamelContext() {
        return context;
    }

    /**
     * Sets the optional {@link CamelContext} used for integration with core capabilities such as Camel Property
     * Placeholders and {@link ClassResolver}.
     *
     * @param context the context to use
     */
    @Override
    public void setCamelContext(CamelContext context) {
        this.context = context;
    }

    /**
     * Parses the value using the Camel Property Placeholder capabilities if a context is provided. Otherwise returns
     * {@code value} as is.
     *
     * @param  value                 the string to replace property tokens in
     * @return                       the value
     *
     * @throws RuntimeCamelException if property placeholders were used and there was an error resolving them
     *
     * @see                          #setCamelContext(CamelContext)
     */
    protected String parsePropertyValue(String value) throws RuntimeCamelException {
        if (this.getCamelContext() != null) {
            try {
                return this.getCamelContext().resolvePropertyPlaceholders(value);
            } catch (Exception e) {
                throw new RuntimeCamelException("Error parsing property value: " + value, e);
            }
        } else {
            return value;
        }
    }

    /**
     * Parses the values using the Camel Property Placeholder capabilities if a context is provided. Otherwise returns
     * {@code values} as is.
     *
     * @param  values                the list of strings to replace property tokens in
     * @return                       the list of strings
     *
     * @throws RuntimeCamelException if property placeholders were used and there was an error resolving them
     *
     * @see                          #parsePropertyValue(String)
     */
    protected List<String> parsePropertyValues(List<String> values) throws RuntimeCamelException {
        if (this.getCamelContext() == null) {
            return values;
        } else {
            List<String> parsedValues = new ArrayList<>(values.size());
            for (String value : values) {
                parsedValues.add(this.parsePropertyValue(value));
            }
            return parsedValues;
        }
    }

    /**
     * Attempts to load a resource using a number of different approaches. The loading of the resource, is attempted by
     * treating the resource as a file path, a class path resource, a URL, and using the Camel Context's
     * {@link ResourceLoader} if a context is available in that order. An exception is thrown if the resource cannot be
     * resolved to readable input stream using any of the above methods.
     *
     * @param  resource    the resource location
     * @return             the input stream for the resource
     * @throws IOException if the resource cannot be resolved using any of the above methods
     */
    protected InputStream resolveResource(String resource) throws IOException {
        ObjectHelper.notNull(getCamelContext(), "CamelContext", this);

        Resource res
                = getCamelContext().getCamelContextExtension().getContextPlugin(ResourceLoader.class).resolveResource(resource);
        if (res == null || !res.exists()) {
            throw new IOException("Could not open " + resource + " as a file, class path resource, or URL.");
        }
        return res.getInputStream();
    }

}
