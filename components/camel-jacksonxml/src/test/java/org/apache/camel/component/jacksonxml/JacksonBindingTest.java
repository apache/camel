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
package org.apache.camel.component.jacksonxml;

import javax.naming.Context;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.binding.BindingComponent;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.binding.DataFormatBinding;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class JacksonBindingTest extends CamelTestSupport {
    protected MockEndpoint results;

    @Test
    public void testMarshalAndUnmarshalPojo() throws Exception {
        TestPojo in = new TestPojo();
        in.setName("Camel");

        results.expectedMessageCount(1);
        results.message(0).body().isInstanceOf(TestPojo.class);
        results.message(0).body().isEqualTo(in);

        template.sendBody("direct:start", in);
        results.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        results = getMockEndpoint("mock:results");
        return new RouteBuilder() {

            @Override
            public void configure() throws Exception {
                from("direct:start").to("jsonmq:orders").to("file:target/copyOfMessages");
                from("jsonmq:orders").to(results);
            }
        };
    }

    @Override
    protected Context createJndiContext() throws Exception {
        Context context = super.createJndiContext();
        JacksonXMLDataFormat format = new JacksonXMLDataFormat(TestPojo.class);
        context.bind("jsonmq", new BindingComponent(new DataFormatBinding(format), "file:target/"));
        return context;
    }
}
