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

import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class GsonJsonDataFormatTest extends GsonMarshalTest {

    @Test
    public void testUnmarshalMap() {
        Map<?, ?> unmarshalled = template.requestBody("direct:json", "{\"pointsOfSale\":{\"pointOfSale\":{\"prodcut\":\"newpad\"}}}", Map.class);
        Map<?, ?> map1 = (Map<?, ?>) unmarshalled.get("pointsOfSale");
        Map<?, ?> map2 = (Map<?, ?>) map1.get("pointOfSale");
        assertEquals("newpad", map2.get("prodcut"), "Don't get the right value");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:in").marshal().json(JsonLibrary.Gson);
                from("direct:back").unmarshal().json(JsonLibrary.Gson).to("mock:reverse");

                from("direct:inPojo").marshal().json(JsonLibrary.Gson);
                from("direct:backPojo").unmarshal().json(JsonLibrary.Gson, TestPojo.class).to("mock:reversePojo");
                
                from("direct:json").unmarshal().json(JsonLibrary.Gson, Map.class);
            }
        };
    }

}
