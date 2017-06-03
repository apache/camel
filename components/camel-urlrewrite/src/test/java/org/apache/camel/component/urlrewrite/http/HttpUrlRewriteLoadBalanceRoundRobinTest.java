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
package org.apache.camel.component.urlrewrite.http;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.urlrewrite.BaseUrlRewriteTest;
import org.apache.camel.component.urlrewrite.HttpUrlRewrite;
import org.apache.camel.impl.JndiRegistry;
import org.junit.Test;

/**
 *
 */
public class HttpUrlRewriteLoadBalanceRoundRobinTest extends BaseUrlRewriteTest {

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();

        HttpUrlRewrite myRewrite = new HttpUrlRewrite();
        myRewrite.setConfigFile("example/urlrewrite2.xml");

        jndi.bind("myRewrite", myRewrite);

        return jndi;
    }

    @Test
    public void testHttpUriRewrite() throws Exception {
        // we should round robin between app2 and app3
        String out = template.requestBody("http://localhost:{{port}}/myapp/products/1234", null, String.class);
        assertEquals("http://localhost:" + getPort2() + "/myapp2/products/index.jsp?product_id=1234", out);

        out = template.requestBody("http://localhost:{{port}}/myapp/products/5678", null, String.class);
        assertEquals("http://localhost:" + getPort2() + "/myapp3/products/index.jsp?product_id=5678", out);

        out = template.requestBody("http://localhost:{{port}}/myapp/products/3333", null, String.class);
        assertEquals("http://localhost:" + getPort2() + "/myapp2/products/index.jsp?product_id=3333", out);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("jetty:http://localhost:{{port}}/myapp?matchOnUriPrefix=true")
                    .loadBalance().roundRobin()
                        .to("http://localhost:{{port2}}/myapp2?bridgeEndpoint=true&throwExceptionOnFailure=false&urlRewrite=#myRewrite")
                        .to("http://localhost:{{port2}}/myapp3?bridgeEndpoint=true&throwExceptionOnFailure=false&urlRewrite=#myRewrite");

                from("jetty:http://localhost:{{port2}}/myapp2?matchOnUriPrefix=true")
                    .transform().simple("${header.CamelHttpUrl}?${header.CamelHttpQuery}");

                from("jetty:http://localhost:{{port2}}/myapp3?matchOnUriPrefix=true")
                    .transform().simple("${header.CamelHttpUrl}?${header.CamelHttpQuery}");
            }
        };
    }
}
