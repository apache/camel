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
package org.apache.camel.component.snmp;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.InvalidPayloadException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.AvailablePortFinder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The trap producer needs a PDU body.
 */
public class TrapProducerBodyTest extends SnmpTestSupport {

    @RegisterExtension
    AvailablePortFinder.Port port = AvailablePortFinder.find();

    @Test
    public void testTextBody() {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:trap", "Hello"));
        InvalidPayloadException cause = assertInstanceOf(InvalidPayloadException.class, e.getCause());
        assertTrue(cause.getMessage().contains("org.snmp4j.PDU"), cause.getMessage());
    }

    @Test
    public void testNoBody() {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:trap", null));
        assertInstanceOf(InvalidPayloadException.class, e.getCause());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:trap").to("snmp:127.0.0.1:" + port.getPort() + "?protocol=udp&type=TRAP&snmpVersion=1");
            }
        };
    }
}
