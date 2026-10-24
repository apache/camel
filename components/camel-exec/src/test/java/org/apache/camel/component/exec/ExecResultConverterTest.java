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
package org.apache.camel.component.exec;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import org.apache.camel.Exchange;
import org.apache.camel.LoggingLevel;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * CAMEL-25971: an ExecResult without output converts to an empty byte array, as it converts to an empty String.
 */
class ExecResultConverterTest extends CamelTestSupport {

    @Test
    void noOutputConvertsToEmptyByteArray() {
        Exchange exchange = new DefaultExchange(context);
        exchange.getIn().setBody(new ExecResult(command(), null, null, 0));

        assertArrayEquals(new byte[0], exchange.getIn().getBody(byte[].class));
        assertEquals("", exchange.getIn().getBody(String.class));
    }

    @Test
    void outputConvertsToByteArray() {
        Exchange exchange = new DefaultExchange(context);
        byte[] out = "hello".getBytes(StandardCharsets.UTF_8);
        exchange.getIn().setBody(new ExecResult(command(), new ByteArrayInputStream(out), null, 0));

        assertArrayEquals(out, exchange.getIn().getBody(byte[].class));
    }

    private static ExecCommand command() {
        return new ExecCommand(
                "true", Collections.emptyList(), null, 1000L, null, null, null, false, LoggingLevel.OFF);
    }
}
