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
package org.apache.camel.tests.typeconverterscan;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class RouteMainTest {

    @Test
    void testLoadTypeConverter() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            context.setLoadTypeConverters(true);

            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:start").convertBodyTo(MyBean.class);
                }
            });

            context.start();

            Object out = context.createProducerTemplate().requestBody("direct:start", "foo:bar");
            assertNotNull(out);

            MyBean my = (MyBean) out;
            assertEquals("foo", my.getA());
            assertEquals("bar", my.getB());
        }
    }
}
