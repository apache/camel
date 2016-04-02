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
package org.apache.camel.test.blueprint.component.rest;
import java.util.Arrays;

import org.apache.camel.model.ToDefinition;
import org.apache.camel.model.rest.CollectionFormat;
import org.apache.camel.model.rest.RestDefinition;
import org.apache.camel.model.rest.RestParamType;
import org.apache.camel.test.blueprint.CamelBlueprintTestSupport;
import org.junit.Test;

public class FromRestGetTest extends CamelBlueprintTestSupport {

    @Override
    protected String getBlueprintDescriptor() {
        return "org/apache/camel/test/blueprint/component/rest/FromRestGetTest.xml";
    }

    protected int getExpectedNumberOfRoutes() {
        return 2 + 3;
    }

    @Test
    public void testFromRestModel() throws Exception {
        assertEquals(getExpectedNumberOfRoutes(), context.getRoutes().size());

        assertEquals(2, context.getRestDefinitions().size());
        RestDefinition rest = context.getRestDefinitions().get(0);
        assertNotNull(rest);
        assertEquals("/say/hello", rest.getPath());
        assertEquals(1, rest.getVerbs().size());
        ToDefinition to = assertIsInstanceOf(ToDefinition.class, rest.getVerbs().get(0).getTo());
        assertEquals("direct:hello", to.getUri());

        rest = context.getRestDefinitions().get(1);
        assertNotNull(rest);
        assertEquals("/say/bye", rest.getPath());
        assertEquals(2, rest.getVerbs().size());
        assertEquals("application/json", rest.getVerbs().get(0).getConsumes());

        assertEquals(2, rest.getVerbs().get(0).getParams().size());
        assertEquals(RestParamType.header, rest.getVerbs().get(0).getParams().get(0).getType());
        assertEquals(RestParamType.query, rest.getVerbs().get(0).getParams().get(1).getType());

        assertEquals("header param description1", rest.getVerbs().get(0).getParams().get(0).getDescription());
        assertEquals("header param description2", rest.getVerbs().get(0).getParams().get(1).getDescription());

        assertEquals("integer", rest.getVerbs().get(0).getParams().get(0).getDataType());
        assertEquals("string", rest.getVerbs().get(0).getParams().get(1).getDataType());
        assertEquals(Arrays.asList("1", "2", "3", "4"), rest.getVerbs().get(0).getParams().get(0).getAllowableValues());
        assertEquals(Arrays.asList("a", "b", "c", "d"), rest.getVerbs().get(0).getParams().get(1).getAllowableValues());
        assertEquals("1", rest.getVerbs().get(0).getParams().get(0).getDefaultValue());
        assertEquals("b", rest.getVerbs().get(0).getParams().get(1).getDefaultValue());

        assertEquals(null, rest.getVerbs().get(0).getParams().get(0).getCollectionFormat());
        assertEquals(CollectionFormat.multi, rest.getVerbs().get(0).getParams().get(1).getCollectionFormat());

        assertEquals("header_count", rest.getVerbs().get(0).getParams().get(0).getName());
        assertEquals("header_letter", rest.getVerbs().get(0).getParams().get(1).getName());
        assertEquals(Boolean.TRUE, rest.getVerbs().get(0).getParams().get(0).getRequired());
        assertEquals(Boolean.FALSE, rest.getVerbs().get(0).getParams().get(1).getRequired());

        assertEquals("300", rest.getVerbs().get(0).getResponseMsgs().get(0).getCode());
        assertEquals("rate", rest.getVerbs().get(0).getResponseMsgs().get(0).getHeaders().get(0).getName());
        assertEquals("Rate limit", rest.getVerbs().get(0).getResponseMsgs().get(0).getHeaders().get(0).getDescription());
        assertEquals("integer", rest.getVerbs().get(0).getResponseMsgs().get(0).getHeaders().get(0).getDataType());
        assertEquals("test msg", rest.getVerbs().get(0).getResponseMsgs().get(0).getMessage());
        assertEquals(Integer.class.getCanonicalName(), rest.getVerbs().get(0).getResponseMsgs().get(0).getResponseModel());
        
        to = assertIsInstanceOf(ToDefinition.class, rest.getVerbs().get(0).getTo());
        assertEquals("direct:bye", to.getUri());

        // the rest becomes routes and the input is a seda endpoint created by the DummyRestConsumerFactory
        getMockEndpoint("mock:update").expectedMessageCount(1);
        template.sendBody("seda:post-say-bye", "I was here");
        assertMockEndpointsSatisfied();

        String out = template.requestBody("seda:get-say-hello", "Me", String.class);
        assertEquals("Hello World", out);
        String out2 = template.requestBody("seda:get-say-bye", "Me", String.class);
        assertEquals("Bye World", out2);
    }

}
