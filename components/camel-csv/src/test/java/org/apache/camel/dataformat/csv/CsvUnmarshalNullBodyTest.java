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
package org.apache.camel.dataformat.csv;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.NoTypeConversionAvailableException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unmarshalling a null body says there is nothing to unmarshal, with a hint that reads a CSV file, not a JSON one
 * (CAMEL-27371).
 */
class CsvUnmarshalNullBodyTest extends CamelTestSupport {

    @Test
    void nullBodyFailsWithACsvHint() {
        CamelExecutionException e
                = assertThrows(CamelExecutionException.class, () -> template.sendBody("direct:unmarshal", null));
        NoTypeConversionAvailableException cause = assertInstanceOf(NoTypeConversionAvailableException.class, e.getCause());
        String message = cause.getMessage();
        assertTrue(message.startsWith("Cannot unmarshal using csv: the body is null, so there is nothing to unmarshal"),
                message);
        assertTrue(message.contains("resource:file:data.csv"), message);
        assertFalse(message.contains("json"), message);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:unmarshal")
                        .unmarshal(new CsvDataFormat())
                        .to("mock:output");
            }
        };
    }
}
