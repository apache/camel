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
package org.apache.camel.component.grok;

import java.util.List;

import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class GrokFileUnmarshalTest extends CamelTestSupport {
    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("file:src/test/resources/org/apache/camel/component/grok/data?fileName=access_log.txt&noop=true")
                        .unmarshal().grok("%{COMMONAPACHELOG}")
                        .to("mock:apachelog");
            }
        };
    }

    @Test
    public void test() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:apachelog");
        result.expectedMessageCount(1);
        result.assertIsSatisfied();
        assertEquals(5, result.getExchanges().get(0).getIn().getBody(List.class).size());
    }
}
