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

package org.apache.camel.component.kamelet;

import java.util.Locale;

import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.model.ModelCamelContext;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.Transformer;
import org.apache.camel.support.DefaultKameletResolver;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class DataTypeActionTest extends CamelTestSupport {

    @Test
    public void testKamelet() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);
        mock.expectedBodyReceived().constant("hello_camel_how_are_you");

        template.sendBody("direct:start", "Hello Camel, how are you?");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void shouldResolveKamelet() throws Exception {
        ModelCamelContext camelContext = new DefaultCamelContext();
        Assertions.assertEquals(0, camelContext.getRouteTemplateDefinitions().size());
        new DefaultKameletResolver().resolve("data-type-action", camelContext);
        Assertions.assertEquals(1, camelContext.getRouteTemplateDefinitions().size());

        Assertions.assertEquals(2, camelContext.getRouteTemplateDefinitions().get(0).getTemplateParameters().size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public static class MyFormat extends Transformer {
                @Override
                public void transform(Message message, DataType from, DataType to) throws Exception {
                    message.setBody(message.getBody(String.class)
                            .replaceAll("\\s", "_")
                            .replaceAll("[^a-zA-Z_]", "")
                            .toLowerCase(Locale.US));
                }
            }

            @Override
            public void configure() {
                transformer().name("myFormat").withJava(MyFormat.class);

                from("direct:start")
                        .to("kamelet:data-type-action?format=myFormat")
                        .to("mock:result");
            }
        };
    }

}
