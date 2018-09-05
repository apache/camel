/**
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
package org.apache.camel.jsonpath;

import java.io.ByteArrayInputStream;

import com.jayway.jsonpath.Option;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class JsonPathBeanStreamCachingTest extends CamelTestSupport {

    @Test
    public void testFullName() throws Exception {
        String json = "{\"person\" : {\"firstname\" : \"foo\", \"middlename\" : \"foo2\", \"lastname\" : \"bar\"}}";
        getMockEndpoint("mock:result").expectedBodiesReceived("foo foo2 bar");

        template.sendBody("direct:start", new ByteArrayInputStream(json.getBytes()));

        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").streamCaching()
                        .bean(FullNameBean.class).to("mock:result");
            }
        };
    }

    protected static class FullNameBean {
        // middle name is optional
        public static String getName(@JsonPath("person.firstname") String first,
                                     @JsonPath(value = "person.middlename", options = Option.SUPPRESS_EXCEPTIONS) String middle,
                                     @JsonPath("person.lastname") String last) {
            if (middle != null) {
                return first + " " + middle + " " + last;
            }
            return first + " " + last;
        }
    }
}
