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
package org.apache.camel.component.rest;

import java.util.List;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.ToDefinition;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.rest.RestOperationParamDefinition;
import org.apache.camel.model.rest.RestParamType;
import org.apache.camel.spi.Registry;
import org.junit.Test;

public class FromRestGetPlaceholderParamTest extends ContextTestSupport {

    @Override
    protected Registry createRegistry() throws Exception {
        Registry answer = super.createRegistry();
        answer.bind("dummy-rest", new DummyRestConsumerFactory());
        return answer;
    }

    @Test
    public void testFromRestModelSingleParam() {
        RestDefinition rest = context.getRestDefinitions().get(0);
        assertNotNull(rest);
        assertEquals("items/", rest.getPath());
        assertEquals(1, rest.getVerbs().size());
        ToDefinition to = assertIsInstanceOf(ToDefinition.class, rest.getVerbs().get(0).getTo());
        assertEquals("direct:hello", to.getUri());

        // Validate params
        List<RestOperationParamDefinition> paramDefinitions = rest.getVerbs().get(0).getParams();
        assertEquals(1, paramDefinitions.size());
        assertEquals(RestParamType.path, paramDefinitions.get(0).getType());
        assertEquals("id", paramDefinitions.get(0).getName());
    }

    @Test
    public void testFromRestModelMultipleParams() {
        RestDefinition rest = context.getRestDefinitions().get(1);
        assertNotNull(rest);
        assertEquals("items/", rest.getPath());
        assertEquals(1, rest.getVerbs().size());
        ToDefinition to = assertIsInstanceOf(ToDefinition.class, rest.getVerbs().get(0).getTo());
        assertEquals("direct:hello", to.getUri());

        // Validate params
        List<RestOperationParamDefinition> paramDefinitions = rest.getVerbs().get(0).getParams();
        assertEquals(3, paramDefinitions.size());
        assertEquals(RestParamType.path, paramDefinitions.get(0).getType());
        assertEquals("id", paramDefinitions.get(0).getName());
        assertEquals(RestParamType.path, paramDefinitions.get(1).getType());
        assertEquals("filename", paramDefinitions.get(1).getName());
        assertEquals(RestParamType.path, paramDefinitions.get(2).getType());
        assertEquals("content-type", paramDefinitions.get(2).getName());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                restConfiguration().host("localhost");
                rest("items/")
                        .get("/{id}")
                        .to("direct:hello");

                rest("items/")
                        .get("{id}/{filename}.{content-type}")
                        .to("direct:hello");

                from("direct:hello")
                        .transform().constant("Hello World");
            }
        };
    }
}
