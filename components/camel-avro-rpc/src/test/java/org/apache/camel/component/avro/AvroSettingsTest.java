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
package org.apache.camel.component.avro;

import org.apache.camel.FailedToCreateRouteException;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class AvroSettingsTest extends AvroTestSupport {

    @Test
    public void testConsumerForUnknownMessage() {
        assertThrows(FailedToCreateRouteException.class, () -> {
            context().addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("avro:http:localhost:" + avroPort + "/notValid?protocolClassName=org.apache.camel.avro.generated.KeyValueProtocol").to("log:test");
                }
            });
        });
    }

    @Test
    public void testProducerForUnknownMessage() {
        assertThrows(FailedToCreateRouteException.class, () -> {
            context().addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:test").to("avro:http:localhost:" + avroPort + "/notValid?protocolClassName=org.apache.camel.avro.generated.KeyValueProtocol");
                }
            });
        });
    }

    @Test
    public void testProducerForNonSingleParamMessage() {
        assertThrows(FailedToCreateRouteException.class, () -> {
            context().addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:test").to("avro:http:localhost:" + avroPort + "/put?protocolClassName=org.apache.camel.avro.generated.KeyValueProtocol&singleParameter=true");
                }
            });
        });
    }
}
