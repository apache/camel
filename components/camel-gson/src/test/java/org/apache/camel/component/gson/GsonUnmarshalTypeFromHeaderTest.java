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
package org.apache.camel.component.gson;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class GsonUnmarshalTypeFromHeaderTest extends CamelTestSupport {

    @Test
    public void testUnmarshalTypeFromHeader() {
        String body = "{\"name\":\"my-name\"}";
        String unmarshallType = "org.apache.camel.component.gson.TestPojo";
        TestPojo pojo = template.requestBodyAndHeader("direct:unmarshalTypeFromHeader", body, GsonConstants.UNMARSHAL_TYPE, unmarshallType, TestPojo.class);
        assertNotNull(pojo);
        assertEquals("my-name", pojo.getName());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:unmarshalTypeFromHeader").unmarshal().json(JsonLibrary.Gson);
            }
        };
    }

}
