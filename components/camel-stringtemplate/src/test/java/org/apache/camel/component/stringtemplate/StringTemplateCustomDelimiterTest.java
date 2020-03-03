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
package org.apache.camel.component.stringtemplate;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class StringTemplateCustomDelimiterTest extends CamelTestSupport {
    private static final String DIRECT_BRACE = "direct:brace";
    private static final String DIRECT_DOLLAR = "direct:dollar";

    @Test
    public void testWithBraceDelimiter() {
        Exchange response = template.request(DIRECT_BRACE, exchange -> exchange.getIn().setBody("Yay !"));

        assertEquals("With brace delimiter Yay !", response.getMessage().getBody().toString().trim());
    }

    @Test
    public void testWithDollarDelimiter() {
        Exchange response = template.request(DIRECT_DOLLAR, exchange -> exchange.getIn().setBody("Yay !"));

        assertEquals("With identical dollar delimiter Yay !", response.getMessage().getBody().toString().trim());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from(DIRECT_BRACE).to("string-template:org/apache/camel/component/stringtemplate/custom-delimiter-brace.tm?delimiterStart={&delimiterStop=}");
                from(DIRECT_DOLLAR).to("string-template:org/apache/camel/component/stringtemplate/custom-delimiter-dollar.tm?delimiterStart=$&delimiterStop=$");
            }
        };
    }
}
