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
package org.apache.camel.component.mongodb.integration;

import java.nio.charset.StandardCharsets;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultCamelContext;
import org.bson.Document;
import org.junit.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MongoDbTypeConvertersTest {
    @Test
    public void convertFromString() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            Document doc = context.getTypeConverter().convertTo(
                    Document.class,
                    "{ \"id\": \"foo\", \"value\": \"the value\"}");

            assertEquals("foo", doc.get("id", String.class));
            assertEquals("the value", doc.get("value", String.class));
        }
    }

    @Test
    public void convertFromBytes() throws Exception {
        try (CamelContext context = new DefaultCamelContext()) {
            Document doc = context.getTypeConverter().convertTo(
                    Document.class,
                    "{ \"id\": \"foo\", \"value\": \"the value\"}".getBytes(StandardCharsets.UTF_8));

            assertEquals("foo", doc.get("id", String.class));
            assertEquals("the value", doc.get("value", String.class));
        }
    }
}
