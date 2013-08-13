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
package org.apache.camel.component.dataformat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;

public class DataFormatEndpointStringTest extends ContextTestSupport {

    public void testUnmarshalUTF8() throws Exception {
        // NOTE: Here we can use a MockEndpoint as we unmarshal the inputstream to String

        // include a UTF-8 char in the text \u0E08 is a Thai elephant
        final String title = "Hello Thai Elephant \u0E08";
        byte[] bytes = title.getBytes("UTF-8");
        InputStream in = new ByteArrayInputStream(bytes);

        template.sendBody("direct:start", in);

        MockEndpoint mock = context.getEndpoint("mock:unmarshal", MockEndpoint.class);
        mock.setExpectedMessageCount(1);
        mock.expectedBodiesReceived(title);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start")
                    .to("dataformat:string:unmarshal?charset=UTF-8")
                    .to("mock:unmarshal");
            }
        };
    }
}
