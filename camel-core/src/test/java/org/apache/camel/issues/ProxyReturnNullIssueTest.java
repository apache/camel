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
package org.apache.camel.issues;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.bean.ProxyHelper;
import org.junit.Test;

/**
 * @version 
 */
public class ProxyReturnNullIssueTest extends ContextTestSupport {

    @Test
    public void testEcho() throws Exception {
        Echo service = ProxyHelper.createProxy(context.getEndpoint("direct:echo"), Echo.class);
        assertEquals("Hello World", service.echo("Hello World"));
    }

    @Test
    public void testEchoNull() throws Exception {
        Echo service = ProxyHelper.createProxy(context.getEndpoint("direct:echo"), Echo.class);
        assertEquals(null, service.echo(null));
    }


    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:echo").bean(new MyEchoBean());
            }
        };
    }

    public interface Echo {
        String echo(String text);
    }

    public static class MyEchoBean implements Echo {

        public String echo(String text) {
            return text;
        }
    }

}
