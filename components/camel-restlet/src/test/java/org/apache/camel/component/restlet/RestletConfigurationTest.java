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
package org.apache.camel.component.restlet;

import java.util.Optional;

import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;
import org.restlet.engine.Engine;
import org.restlet.engine.converter.ConverterHelper;
import org.restlet.ext.gson.GsonConverter;
import org.restlet.ext.jackson.JacksonConverter;

public class RestletConfigurationTest extends RestletTestSupport {
    @Override
    protected void doPreSetup() {
        assertPresent(GsonConverter.class);
        assertPresent(JacksonConverter.class);
    }

    @Test
    public void testConfiguration() throws Exception {
        assertNotPresent(GsonConverter.class);
        assertPresent(JacksonConverter.class);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                restConfiguration()
                    .component("restlet")
                    .componentProperty("enabledConverters", "JacksonConverter");

                from("direct:start")
                    .to("restlet:http://localhost:" + portNum + "/users/1/basic")
                    .to("log:reply");
            }
        };
    }

    protected <T extends ConverterHelper> Optional<ConverterHelper> findByType(Class<T> type) {
        return Engine.getInstance().getRegisteredConverters().stream().filter(type::isInstance).findFirst();
    }

    protected <T extends ConverterHelper> void assertPresent(Class<T> type) {
        assertTrue(type.getSimpleName(), findByType(type).isPresent());
    }

    protected <T extends ConverterHelper> void assertNotPresent(Class<T> type) {
        assertFalse(type.getSimpleName(), findByType(type).isPresent());
    }
}
