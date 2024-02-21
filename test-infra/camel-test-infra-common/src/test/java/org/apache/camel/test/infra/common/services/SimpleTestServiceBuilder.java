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

package org.apache.camel.test.infra.common.services;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SimpleTestServiceBuilder<T extends TestService> implements TestServiceBuilder<T> {
    private static final Logger LOG = LoggerFactory.getLogger(SimpleTestServiceBuilder.class);

    private final Map<String, Supplier<T>> mappings = new HashMap<>();
    private final String name;
    private String propertyNameFormat = "%s.instance.type";

    public SimpleTestServiceBuilder(String name) {
        this.name = name;
    }

    public SimpleTestServiceBuilder<T> addMapping(String name, Supplier<T> supplier) {
        mappings.put(name, supplier);

        return this;
    }

    public SimpleTestServiceBuilder<T> addLocalMapping(Supplier<T> supplier) {
        mappings.put(localMappingKey(), supplier);

        return this;
    }

    public SimpleTestServiceBuilder<T> addRemoteMapping(Supplier<T> supplier) {
        mappings.put(remoteMappingKey(), supplier);

        return this;
    }

    public SimpleTestServiceBuilder<T> withPropertyNameFormat(String propertyNameFormat) {
        this.propertyNameFormat = propertyNameFormat;

        return this;
    }

    @Override
    public T build() {
        String defaultName = localMappingKey();
        String propertyName = String.format(propertyNameFormat, name);
        String instanceType = System.getProperty(propertyName, defaultName);

        Supplier<T> supplier = mappings.get(instanceType);
        if (supplier == null) {
            String valid = String.join(", ", mappings.keySet());

            LOG.error("Invalid instance type: {}. Must one of: {}", instanceType, valid);
            throw new UnsupportedOperationException("Invalid instance type: " + instanceType);
        }

        return supplier.get();
    }

    private String localMappingKey() {
        return String.format("local-%s-container", name);
    }

    private String remoteMappingKey() {
        return "remote";
    }
}
