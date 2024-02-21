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
package org.apache.camel.jaxb;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.converter.jaxb.FallbackTypeConverter;
import org.apache.camel.converter.jaxb.message.Message;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

public class FallbackTypeConverterObjectFactoryEnabledTest extends CamelTestSupport {

    @Test
    public void testObjectFactoryTrue() throws Exception {
        Message in = new Message("Hello World");
        getMockEndpoint("mock:a").expectedBodiesReceived(in);

        template.sendBody("direct:a", in);

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        context.getGlobalOptions().put(FallbackTypeConverter.OBJECT_FACTORY, "true");
        return new RouteBuilder(context) {

            @Override
            public void configure() {
                from("direct:a").convertBodyTo(String.class).to("direct:b");
                from("direct:b").convertBodyTo(Message.class).to("mock:a");
            }

        };
    }

}
