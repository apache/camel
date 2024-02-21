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
package org.apache.camel.component.mongodb;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MongoDbEndpointHostsTest extends CamelTestSupport {

    @Test
    void testMongoDbEndpoint() {
        MongoDbEndpoint mongoDb
                = context.getEndpoint("mongodb:dummy?hosts=localhost&database=test&collection=test&operation=findAll",
                        MongoDbEndpoint.class);
        assertNotNull(mongoDb);
        assertNotNull(mongoDb.getMongoConnection());
        assertNotNull(mongoDb.getMongoConnection().getDatabase("test"));
        assertEquals("test", mongoDb.getCollection());
        assertEquals("findAll", mongoDb.getOperation().toString());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:dbFromHost").to("mongodb:dummy?hosts=localhost&database=test&collection=test&operation=findAll");
            }
        };
    }

}
