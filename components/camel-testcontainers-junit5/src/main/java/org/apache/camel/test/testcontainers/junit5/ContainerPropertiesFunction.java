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
package org.apache.camel.test.testcontainers.junit5;

import java.util.List;

import org.apache.camel.spi.PropertiesFunction;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.StringHelper;
import org.testcontainers.containers.GenericContainer;

public class ContainerPropertiesFunction implements PropertiesFunction {
    private final List<GenericContainer<?>> containers;

    public ContainerPropertiesFunction(List<GenericContainer<?>> containers) {
        this.containers = ObjectHelper.notNull(containers, "Containers");
    }

    @Override
    public String getName() {
        return "container";
    }

    @Override
    public String apply(String remainder) {
        final String type = StringHelper.before(remainder, ":");
        final String query = StringHelper.after(remainder, ":");

        if (ObjectHelper.isEmpty(type)) {
            throw new IllegalArgumentException("container function syntax is container:type:query");
        }

        if ("host".equalsIgnoreCase(type)) {
            String name = StringHelper.after(remainder, ":");

            if (ObjectHelper.isEmpty(name)) {
                throw new IllegalArgumentException("unable to determine container name");
            }

            return Containers.lookup(containers, StringHelper.after(remainder, ":")).getContainerIpAddress();
        }

        if ("port".equalsIgnoreCase(type)) {
            String port = StringHelper.before(query, "@");
            String name = StringHelper.after(query, "@");

            if (ObjectHelper.isEmpty(port)) {
                throw new IllegalArgumentException("unable to determine original port");
            }

            if (ObjectHelper.isEmpty(name)) {
                throw new IllegalArgumentException("unable to determine container name");
            }

            return Integer.toString(Containers.lookup(containers, name).getMappedPort(Integer.parseInt(port)));
        }

        throw new IllegalArgumentException("Unsupported type: " + type);
    }
}
