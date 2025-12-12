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
package org.apache.camel.component.djl;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

public class NlpQuestionAnswerTest extends CamelTestSupport {

    @BeforeAll
    public static void setupDefaultEngine() {
        // Since Apache MXNet is discontinued, prefer PyTorch as the default engine
        System.setProperty("ai.djl.default_engine", "PyTorch");
    }

    @Test
    void testDJL() throws Exception {
        var mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        mock.await();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("timer:testDJL?repeatCount=1")
                        .setBody(constant((Object) new String[] {
                                "When did BBC Japan start broadcasting?",
                                "BBC Japan was a general entertainment channel, which operated between December 2004 and April 2006. It ceased operations after its Japanese distributor folded."
                        }))
                        .to("djl:nlp/question_answer?artifactId=ai.djl.pytorch:bertqa:0.0.1")
                        .log("Answer: ${body}")
                        .to("mock:result");
            }
        };
    }

}
