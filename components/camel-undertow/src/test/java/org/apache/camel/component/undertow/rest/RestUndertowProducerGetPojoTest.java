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
package org.apache.camel.component.undertow.rest;

import org.apache.camel.LoggingLevel;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.undertow.BaseUndertowTest;
import org.apache.camel.model.rest.RestBindingMode;
import org.apache.camel.spi.BeanIntrospection;
import org.apache.camel.support.PluginHelper;
import org.junit.jupiter.api.Test;

import static org.apache.camel.test.junit5.TestSupport.assertIsInstanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class RestUndertowProducerGetPojoTest extends BaseUndertowTest {

    @Test
    public void testUndertowGetPojoRequest() {
        // should not use reflection when using rest binding in the rest producer
        BeanIntrospection bi = PluginHelper.getBeanIntrospection(context);
        bi.setLoggingLevel(LoggingLevel.INFO);
        bi.resetCounters();

        assertEquals(0, bi.getInvokedCounter());

        String url = "rest:get:users/lives?outType=" + CountryPojo.class.getName();
        Object out = template.requestBody(url, (String) null);

        assertNotNull(out);
        CountryPojo pojo = assertIsInstanceOf(CountryPojo.class, out);
        assertEquals("EN", pojo.getIso());
        assertEquals("England", pojo.getCountry());

        assertEquals(0, bi.getInvokedCounter());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // configure to use undertow on localhost with the given port
                // and enable auto binding mode
                restConfiguration()
                        .component("undertow").host("localhost").port(getPort()).bindingMode(RestBindingMode.json)
                        .componentProperty("muteException", "true")
                        .endpointProperty("keepAlive", "false");

                // use the rest DSL to define the rest services
                rest("/users/")
                        .get("lives")
                        // just return the default country here
                        .to("direct:start");

                CountryPojo country = new CountryPojo();
                country.setIso("EN");
                country.setCountry("England");

                from("direct:start").transform().constant(country);
            }
        };
    }

}
