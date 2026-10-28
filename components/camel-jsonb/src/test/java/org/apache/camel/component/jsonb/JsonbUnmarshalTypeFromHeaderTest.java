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
package org.apache.camel.component.jsonb;

import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonDataFormat;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * The CamelJsonbUnmarshallType header only selects the unmarshal type when allowUnmarshallType is enabled.
 */
class JsonbUnmarshalTypeFromHeaderTest extends CamelTestSupport {

    private static final String BODY = "{\"name\":\"my-name\"}";

    @Test
    void unmarshalTypeFromHeaderWhenAllowed() {
        Object result = template.requestBodyAndHeader("direct:allowed", BODY, JsonbDataFormat.UNMARSHAL_TYPE,
                TestPojo.class.getName());

        TestPojo pojo = assertInstanceOf(TestPojo.class, result);
        assertEquals("my-name", pojo.getName());
    }

    @Test
    void unmarshalTypeFromHeaderIsIgnoredByDefault() {
        Object result = template.requestBodyAndHeader("direct:default", BODY, JsonbDataFormat.UNMARSHAL_TYPE,
                TestPojo.class.getName());

        assertInstanceOf(Map.class, result);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:allowed").unmarshal(new JsonDataFormat(JsonLibrary.Jsonb).allowUnmarshallType(true));
                from("direct:default").unmarshal().json(JsonLibrary.Jsonb);
            }
        };
    }
}
