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
package org.apache.camel.impl.engine;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.FailedToStartComponentException;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultComponent;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class FailedToStartComponentNoMessageTest {

    private static final IllegalStateException CAUSE = new IllegalStateException();

    @Test
    public void testCauseWithoutMessage() {
        CamelContext context = new DefaultCamelContext();
        context.addComponent("broken", new DefaultComponent() {
            @Override
            protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) {
                return null;
            }

            @Override
            protected void doStart() throws Exception {
                throw CAUSE;
            }
        });

        Exception e = assertThrows(Exception.class, context::start);

        FailedToStartComponentException fe = assertInstanceOf(FailedToStartComponentException.class, e);
        assertEquals("broken", fe.getComponentName());
        assertEquals("Failed to start component broken because of java.lang.IllegalStateException", fe.getMessage());
        assertSame(CAUSE, fe.getCause());
    }
}
