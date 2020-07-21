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
package org.apache.camel.dataformat.beanio;

import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BeanIOUnmarshalSingleObjectTest extends CamelTestSupport {

    private static final String NEW_LINE = "\n";
    private static final String INPUT = "1234:Content starts from here" + NEW_LINE + "then continues" + NEW_LINE + "and ends here.";

    @Test
    void testMultiLineContentUnmarshal() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        template.sendBody("direct:unmarshal", INPUT);

        mock.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                BeanIODataFormat format = new BeanIODataFormat("org/apache/camel/dataformat/beanio/single-object-mapping.xml", "keyValueStream");
                // turn on single mode
                format.setUnmarshalSingleObject(true);

                from("direct:unmarshal").unmarshal(format).process(new Processor() {
                    public void process(Exchange exchange) throws Exception {
                        Map body = (Map) exchange.getIn().getBody();
                        assertEquals(":", body.get("separator"));
                        assertEquals("1234", body.get("key"));
                        assertEquals(INPUT.substring(5), body.get("value"));
                    }
                }).marshal(format).to("mock:result");
            }
        };
    }

}
