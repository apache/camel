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
package org.apache.camel.component.mongodb.converters;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.bson.Document;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MongoDbFallbackConverterTest {

    @Test
    void storesPojoDatesAsTimestampsInDeclaredOrder() throws Exception {
        Document document = (Document) MongoDbFallbackConverter.convertTo(Document.class, null, new Order(), null);

        assertEquals(0L, document.get("created"));
        assertEquals(List.of("zone", "created", "amount"), new ArrayList<>(document.keySet()));
    }

    public static class Order {
        public String zone = "eu";
        public Date created = new Date(0);
        public int amount = 1;
    }
}
