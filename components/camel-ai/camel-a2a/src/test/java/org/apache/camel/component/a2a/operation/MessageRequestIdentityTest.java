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
package org.apache.camel.component.a2a.operation;

import java.util.List;
import java.util.stream.Stream;

import org.apache.camel.Exchange;
import org.apache.camel.component.a2a.A2AConstants;
import org.apache.camel.component.a2a.A2ADataFormat;
import org.apache.camel.component.a2a.model.Message;
import org.apache.camel.component.a2a.model.SendMessageRequest;
import org.apache.camel.component.a2a.model.TextPart;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import static org.assertj.core.api.Assertions.assertThat;

class MessageRequestIdentityTest {

    private final Exchange exchange = new DefaultExchange(new DefaultCamelContext());
    private final Message incoming = Message.builder().messageId("incoming").taskId("outer-task").contextId("outer-context")
            .role(Message.Role.ROLE_USER).parts(List.of(new TextPart("hello"))).build();

    @BeforeEach
    void setUp() {
        SendMessageRequest request = new SendMessageRequest();
        request.setMessage(incoming);
        new MessageSendOperation().parseRequest(exchange, request, A2ADataFormat.POJO);
    }

    static Stream<A2AOperation> operations() {
        return Stream.of(new MessageSendOperation(), new MessageStreamOperation());
    }

    @ParameterizedTest
    @MethodSource("operations")
    void routeSuppliedEqualCopyRetainsIds(A2AOperation operation) {
        Message copy = Message.builder(incoming).build();
        assertThat(copy).isNotSameAs(incoming).isEqualTo(incoming);
        exchange.getMessage().setBody(copy);

        SendMessageRequest request = (SendMessageRequest) operation.buildRequest(exchange);

        assertThat(request.getMessage()).isEqualTo(copy);
    }

    @ParameterizedTest
    @MethodSource("operations")
    void routeSuppliedEditedCopyRetainsIds(A2AOperation operation) {
        Message copy = Message.builder(incoming).parts(List.of(new TextPart("edited"))).build();
        exchange.getMessage().setBody(copy);

        SendMessageRequest request = (SendMessageRequest) operation.buildRequest(exchange);

        assertThat(request.getMessage()).isEqualTo(copy);
    }

    @ParameterizedTest
    @MethodSource("operations")
    void routeCanClearCopiedIdsForNewConversation(A2AOperation operation) {
        Message copy = Message.builder(incoming).taskId(null).contextId(null).build();
        exchange.getMessage().setBody(copy);

        SendMessageRequest request = (SendMessageRequest) operation.buildRequest(exchange);

        assertThat(request.getMessage()).isEqualTo(copy);
        assertThat(exchange.getProperty(A2AConstants.REQUEST_MESSAGE)).isSameAs(incoming);
        assertThat(incoming.taskId()).isEqualTo("outer-task");
        assertThat(incoming.contextId()).isEqualTo("outer-context");
    }

    @ParameterizedTest
    @MethodSource("operations")
    void routeCanSelectDownstreamIds(A2AOperation operation) {
        Message copy = Message.builder(incoming).taskId("peer-task").contextId("peer-context").build();
        exchange.getMessage().setBody(copy);

        SendMessageRequest request = (SendMessageRequest) operation.buildRequest(exchange);

        assertThat(request.getMessage()).isEqualTo(copy);
    }
}
