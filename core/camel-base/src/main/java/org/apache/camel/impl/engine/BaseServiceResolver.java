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

import java.util.Optional;

import org.apache.camel.CamelContext;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.spi.FactoryFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BaseServiceResolver<T> {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    protected final String factoryKey;
    protected final Class<T> factoryClass;

    public BaseServiceResolver(String factoryKey, Class<T> factoryClass) {
        this.factoryKey = factoryKey;
        this.factoryClass = factoryClass;
    }

    public Optional<T> resolve(CamelContext context) {
        // use factory finder to find a custom implementations
        Class<?> type = null;
        try {
            FactoryFinder finder = context.adapt(ExtendedCamelContext.class).getFactoryFinder(FactoryFinder.DEFAULT_PATH);
            type = finder.findClass(factoryKey).orElse(null);
        } catch (Exception e) {
            // ignore
        }

        if (type != null) {
            if (log.isDebugEnabled()) {
                log.debug("Found {}: {} via: {}{}", factoryClass.getSimpleName(), type.getName(), FactoryFinder.DEFAULT_PATH, factoryKey);
            }
            if (factoryClass.isAssignableFrom(type)) {
                T answer = factoryClass.cast(context.getInjector().newInstance(type, false));
                log.debug("Detected and using {}: {}", factoryClass.getSimpleName(), answer);
                return Optional.of(answer);
            } else {
                throw new IllegalArgumentException("Type is not a " + factoryClass.getSimpleName() + " implementation. Found: " + type.getName());
            }
        }
        return Optional.empty();
    }

}
