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
package org.apache.camel.component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.chatscript.ChatScriptMessage;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.language.SimpleExpression;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class ChatScriptComponentTest extends CamelTestSupport {

    @Test
    public void testChatScript() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);       
        Thread.sleep(100);
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws JsonProcessingException {
                String g = "CS" + Math.random();
                ChatScriptMessage rqMsg = new ChatScriptMessage(g, "", "");
                ChatScriptMessage rq2Msg = new ChatScriptMessage(g, "", "Hello");
                ChatScriptMessage rq3Msg = new ChatScriptMessage(g, "", "No");
                String rq = "";
                String rq2 = "";
                String rq3 = "";
                try {
                        rq = new ObjectMapper().writeValueAsString(rqMsg);
                        rq2 = new ObjectMapper().writeValueAsString(rq2Msg);
                        rq3 = new ObjectMapper().writeValueAsString(rq3Msg);
                } catch (JsonProcessingException e) {
                        e.printStackTrace();
                }
                from("timer://foo?repeatCount=1")
                .setBody(new SimpleExpression(rq))
                .to("chatscript://localhost:1024/Harry?resetchat=true")
                .log("Response 2 = ${body}")
                .setBody(new SimpleExpression(rq2))
                .to("chatscript://localhost:1024/Harry")
                .log("Response 3 = ${body}")
                .setBody(new SimpleExpression(rq3))
                .to("chatscript://localhost:1024/Harry")
                .log("Response 4 = ${body}")
                    .to("mock:result");
            }
        };
    }
}
