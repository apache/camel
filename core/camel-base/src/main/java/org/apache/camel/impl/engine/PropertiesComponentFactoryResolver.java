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
package org.apache.camel.impl.engine;

import java.io.IOException;

import org.apache.camel.CamelContext;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.spi.PropertiesComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory resolver to find the {@link org.apache.camel.spi.PropertiesComponent} from the classpath.
 */
public class PropertiesComponentFactoryResolver {

    public static final String RESOURCE_PATH = "META-INF/services/org/apache/camel/";

    private static final Logger LOG = LoggerFactory.getLogger(PropertiesComponentFactoryResolver.class);

    private FactoryFinder factoryFinder;

    public PropertiesComponent resolve(CamelContext context) {
        // use factory finder to find a custom implementations
        Class<?> type = null;
        try {
            type = findFactory("properties-component-factory", context);
        } catch (Exception e) {
            // ignore
        }

        if (type != null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Found PropertiesComponent: {} via: {}{}", type.getName(), factoryFinder.getResourcePath(), "properties-component-factory");
            }
            if (PropertiesComponent.class.isAssignableFrom(type)) {
                PropertiesComponent answer = (PropertiesComponent) context.getInjector().newInstance(type, false);
                LOG.debug("Detected and using PropertiesComponent: {}", answer);
                return answer;
            } else {
                throw new IllegalArgumentException("Type is not a PropertiesComponent implementation. Found: " + type.getName());
            }
        }

        // use default component
        return new org.apache.camel.component.properties.PropertiesComponent();
    }

    private Class<?> findFactory(String name, CamelContext context) throws IOException {
        if (factoryFinder == null) {
            factoryFinder = context.adapt(ExtendedCamelContext.class).getFactoryFinder(RESOURCE_PATH);
        }
        return factoryFinder.findClass(name).orElse(null);
    }

}
