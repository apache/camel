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
package org.apache.camel.component.clickup.util;

import java.io.IOException;
import java.io.InputStream;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.CamelContext;
import org.apache.camel.component.clickup.ClickUpComponent;
import org.apache.camel.test.AvailablePortFinder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeAll;

/**
 * A support test class for ClickUp tests.
 */
public class ClickUpTestSupport extends CamelTestSupport {

    protected static volatile int port;

    private ClickUpMockRoutes mockRoutes;

    @BeforeAll
    public static void initPort() {
        port = AvailablePortFinder.getNextAvailable();
    }

    /**
     * Retrieves a response from a JSON file on classpath.
     *
     * @param  fileName the filename in the classpath
     * @param  clazz    the target class
     * @param  <T>      the type of the returned object
     * @return          the object representation of the JSON file
     */
    public static <T> T getJSONResource(String fileName, Class<T> clazz) {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream stream = ClickUpTestSupport.class.getClassLoader().getResourceAsStream(fileName)) {
            T value = mapper.readValue(stream, clazz);
            return value;
        } catch (IOException e) {
            throw new IllegalArgumentException("Unable to load file " + fileName, e);
        }
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        final CamelContext context = super.createCamelContext();
        final ClickUpComponent component = new ClickUpComponent();

        context.addComponent("clickup", component);

        return context;
    }

    protected ClickUpMockRoutes getMockRoutes() {
        if (mockRoutes == null) {
            mockRoutes = createMockRoutes();
        }
        return mockRoutes;
    }

    protected ClickUpMockRoutes createMockRoutes() {
        throw new UnsupportedOperationException();
    }

}
