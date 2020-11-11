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
package org.apache.camel.zipkin;

import brave.Span;
import org.apache.camel.Message;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CamelRequestTest {

    @Test
    public void testCamelRequest() {
        Message message = mock(Message.class);
        when(message.getHeader("X-B3-TraceId", String.class)).thenReturn("924c5b125daaaec8");
        CamelRequest request = new CamelRequest(message, Span.Kind.PRODUCER);
        request.setHeader("X-B3-SpanId", "db1ccb94946711b0");
        assertThat(request.spanKind()).isEqualTo(Span.Kind.PRODUCER);
        assertThat(request.unwrap()).isEqualTo(message);
        verify(message).setHeader("X-B3-SpanId", "db1ccb94946711b0");
        assertThat(request.getHeader("X-B3-TraceId")).isEqualTo("924c5b125daaaec8");
    }

}
