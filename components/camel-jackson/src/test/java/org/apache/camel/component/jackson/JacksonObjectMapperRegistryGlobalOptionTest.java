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
package org.apache.camel.component.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationModule;
import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class JacksonObjectMapperRegistryGlobalOptionTest extends CamelTestSupport {

    @BindToRegistry("myMapper")
    private ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testMarshalAndUnmarshalMapGlobalOverride() throws Exception {
        //Add Jaxb Module to global object mapper
        objectMapper.registerModule(new JaxbAnnotationModule());
        TestJAXBPojo in = new TestJAXBPojo();
        in.setName("Camel");

        MockEndpoint mock = getMockEndpoint("mock:reverse");
        mock.expectedMessageCount(1);
        mock.message(0).body().isInstanceOf(TestJAXBPojo.class);
        mock.message(0).body().isEqualTo(in);

        Object marshalled = template.requestBody("direct:inGlobal", in);
        String marshalledAsString = context.getTypeConverter().convertTo(String.class, marshalled);
        assertEquals("{\"PojoName\":\"Camel\"}", marshalledAsString);

        template.sendBody("direct:backGlobal", marshalled);

        mock.assertIsSatisfied();

    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {

                getContext().getGlobalOptions().put("jackson.json.autoDiscoverObjectMapper", "true");

                from("direct:inGlobal").marshal().json(JsonLibrary.Jackson);
                from("direct:backGlobal").unmarshal().json(JsonLibrary.Jackson, TestJAXBPojo.class).to("mock:reverse");
            }
        };
    }

}
