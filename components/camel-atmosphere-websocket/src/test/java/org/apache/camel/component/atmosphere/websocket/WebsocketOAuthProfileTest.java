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
package org.apache.camel.component.atmosphere.websocket;

import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

public class WebsocketOAuthProfileTest extends CamelTestSupport {

    @Test
    public void oauthProfileIsNotSupported() {
        Exception exception = assertThrows(Exception.class,
                () -> context.getEndpoint("atmosphere-websocket:///secure?oauthProfile=myprofile"));

        Throwable current = exception;
        while (current != null) {
            if (current instanceof IllegalArgumentException
                    && "The atmosphere-websocket component does not support oauthProfile".equals(current.getMessage())) {
                return;
            }
            current = current.getCause();
        }
        fail("Expected cause chain to contain unsupported oauthProfile failure");
    }
}
