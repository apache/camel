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
package org.apache.camel.converter.jaxb;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.PropertyConfigurer;
import org.apache.camel.spi.RestBindingJaxbDataFormatFactory;
import org.apache.camel.spi.RestConfiguration;
import org.apache.camel.spi.annotations.JdkService;
import org.apache.camel.support.PropertyBindingSupport;

/**
 * JAXB based {@link RestBindingJaxbDataFormatFactory}.
 */
@JdkService(RestBindingJaxbDataFormatFactory.FACTORY)
public class JaxbRestBindingJaxbDataFormatFactory implements RestBindingJaxbDataFormatFactory {

    @Override
    public void setupJaxb(CamelContext camelContext, RestConfiguration config, String type, String outType, DataFormat jaxb, DataFormat outJaxb) throws Exception {
        // lookup configurer
        PropertyConfigurer configurer = camelContext.adapt(ExtendedCamelContext.class).getConfigurerResolver().resolvePropertyConfigurer("jaxb-dataformat-configurer", camelContext);
        if (configurer == null) {
            throw new IllegalStateException("Cannot find configurer for dataformat: jaxb");
        }

        PropertyBindingSupport.Builder builder = PropertyBindingSupport.build()
                .withCamelContext(camelContext)
                .withConfigurer(configurer)
                .withTarget(jaxb);
        if (type != null) {
            String typeName = type.endsWith("[]") ? type.substring(0, type.length() - 2) : type;
            builder.withProperty("contextPath", typeName);
            builder.withProperty("contextPathIsClassName", "true");
        }
        setAdditionalConfiguration(config, "xml.in.", builder);
        builder.bind();

        builder = PropertyBindingSupport.build()
                .withCamelContext(camelContext)
                .withConfigurer(configurer)
                .withTarget(outJaxb);
        if (outType != null) {
            String typeName = outType.endsWith("[]") ? outType.substring(0, outType.length() - 2) : outType;
            builder.withProperty("contextPath", typeName);
            builder.withProperty("contextPathIsClassName", "true");
        } else if (type != null) {
            // fallback and use the context from the input
            String typeName = type.endsWith("[]") ? type.substring(0, type.length() - 2) : type;
            builder.withProperty("contextPath", typeName);
            builder.withProperty("contextPathIsClassName", "true");
        }
        setAdditionalConfiguration(config, "xml.out.", builder);
        builder.bind();
    }

    private void setAdditionalConfiguration(RestConfiguration config, String prefix, PropertyBindingSupport.Builder builder) throws Exception {
        if (config.getDataFormatProperties() != null && !config.getDataFormatProperties().isEmpty()) {
            // must use a copy as otherwise the options gets removed during
            // introspection setProperties
            Map<String, Object> copy = new HashMap<>();

            // filter keys on prefix
            // - either its a known prefix and must match the prefix parameter
            // - or its a common configuration that we should always use
            for (Map.Entry<String, Object> entry : config.getDataFormatProperties().entrySet()) {
                String key = entry.getKey();
                String copyKey;
                boolean known = isKeyKnownPrefix(key);
                if (known) {
                    // remove the prefix from the key to use
                    copyKey = key.substring(prefix.length());
                } else {
                    // use the key as is
                    copyKey = key;
                }
                if (!known || key.startsWith(prefix)) {
                    copy.put(copyKey, entry.getValue());
                }
            }

            builder.withProperties(copy);
        }
    }

    private boolean isKeyKnownPrefix(String key) {
        return key.startsWith("json.in.") || key.startsWith("json.out.") || key.startsWith("xml.in.") || key.startsWith("xml.out.");
    }

}
