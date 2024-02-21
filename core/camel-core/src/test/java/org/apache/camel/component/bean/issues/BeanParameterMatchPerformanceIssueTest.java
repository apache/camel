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
package org.apache.camel.component.bean.issues;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class BeanParameterMatchPerformanceIssueTest extends ContextTestSupport {

    @Test
    public void testPerformance() throws Exception {
        String s = template.requestBody("direct:a", "a", String.class);
        Assertions.assertEquals("Hello slow", s);

        s = template.requestBody("direct:b", "b", String.class);
        Assertions.assertEquals("Hello fast", s);

        s = template.requestBody("direct:c", "c", String.class);
        Assertions.assertEquals("Hello fast", s);

        s = template.requestBody("direct:d", "d", String.class);
        Assertions.assertEquals("Hello 'fast'", s);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.getRegistry().bind("myBean", new MyBean());

                from("direct:a")
                        .to("bean:myBean?method=myMethod(slow)");

                from("direct:b")
                        .to("bean:myBean?method=myMethod('fast')");

                from("direct:c")
                        .to("bean:myBean?method=myMethod(\"fast\")");

                from("direct:d")
                        .to("bean:myBean?method=myMethod(\"'fast'\")");
            }
        };
    }

    private static class MyBean {

        public String myMethod(String str) {
            return "Hello " + str;
        }
    }
}
