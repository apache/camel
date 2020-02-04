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
package org.apache.camel.impl;

import java.io.IOException;

import org.apache.camel.CamelContext;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.FactoryFinder;
import org.apache.camel.spi.ModelToXMLDumper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Factory to create the {@link ModelToXMLDumper} implementation to be used.
 *
 * @see ModelToXMLDumper
 */
public class ModelToXMLDumperResolver {

    public static final String RESOURCE_PATH = "META-INF/services/org/apache/camel/";

    private static final Logger LOG = LoggerFactory.getLogger(ModelToXMLDumperResolver.class);

    private FactoryFinder factoryFinder;

    public ModelToXMLDumper resolve(CamelContext context) {
        // use factory finder to find a custom implementations
        Class<?> type = null;
        try {
            type = findFactory("modelxml-dumper", context);
        } catch (Exception e) {
            // ignore
        }

        if (type != null) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Found ModelToXMLDumper: {} via: {}{}", type.getName(), factoryFinder.getResourcePath(), "modelxml-dumper");
            }
            if (ModelToXMLDumper.class.isAssignableFrom(type)) {
                ModelToXMLDumper answer = (ModelToXMLDumper) context.getInjector().newInstance(type, false);
                LOG.info("Detected and using ModelToXMLDumper: {}", answer);
                return answer;
            } else {
                throw new IllegalArgumentException("Type is not a ModelToXMLDumper implementation. Found: " + type.getName());
            }
        }

        throw new RuntimeCamelException("Cannot find XML routes dumper in classpath. Add camel-xml-jaxb to the classpath.");
    }

    private Class<?> findFactory(String name, CamelContext context) throws IOException {
        if (factoryFinder == null) {
            factoryFinder = context.adapt(ExtendedCamelContext.class).getFactoryFinder(RESOURCE_PATH);
        }
        return factoryFinder.findClass(name).orElse(null);
    }

}

