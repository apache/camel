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
package org.apache.camel.component.bean;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.spi.Registry;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.when;

public class MockitoSpyForClassTest extends ContextTestSupport {

    @Test
    public void testCallingSpy() throws Exception {
        Object response = template.requestBody("direct:start", "anything");
        assertEquals("mocked answer", response);
    }

    @Override
    protected Registry createRegistry() throws Exception {
        MyService mockService = Mockito.spy(new MyService());
        when(mockService.doSomething(any())).thenReturn("mocked answer");

        Registry answer = super.createRegistry();
        answer.bind("myService", mockService);
        return answer;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:start").bean("bean:myService");
            }
        };
    }

    public static class MyService {
        public String doSomething(String body) {
            return "real answer";
        }
    }

}
