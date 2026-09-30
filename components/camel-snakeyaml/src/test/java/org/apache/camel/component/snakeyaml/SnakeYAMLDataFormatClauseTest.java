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
package org.apache.camel.component.snakeyaml;

import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The YAML data format from the data format clause: {@code .marshal().yaml()}, as the documentation shows.
 */
public class SnakeYAMLDataFormatClauseTest extends CamelTestSupport {

    @Test
    public void marshalAndUnmarshal() {
        String yaml = template.requestBody("direct:marshal", Map.of("name", "Camel"), String.class);
        assertEquals("{name: Camel}", yaml.trim());
        assertEquals(Map.of("name", "Camel"), template.requestBody("direct:unmarshal", yaml, Map.class));
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:marshal").marshal().yaml();
                from("direct:unmarshal").unmarshal().yaml();
            }
        };
    }
}
