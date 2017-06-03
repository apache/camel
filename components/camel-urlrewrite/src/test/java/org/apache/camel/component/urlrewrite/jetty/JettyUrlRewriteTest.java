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
package org.apache.camel.component.urlrewrite.jetty;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.urlrewrite.BaseUrlRewriteTest;
import org.apache.camel.component.urlrewrite.HttpUrlRewrite;
import org.apache.camel.impl.JndiRegistry;
import org.junit.Test;

/**
 *
 */
public class JettyUrlRewriteTest extends BaseUrlRewriteTest {

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();

        // START SNIPPET: e1
        HttpUrlRewrite myRewrite = new HttpUrlRewrite();
        myRewrite.setConfigFile("example/urlrewrite2.xml");
        // END SNIPPET: e1

        jndi.bind("myRewrite", myRewrite);

        return jndi;
    }

    @Test
    public void testJettyUriRewrite() throws Exception {
        String out = template.requestBody("jetty:http://localhost:{{port}}/myapp/products/1234", null, String.class);
        assertEquals("http://localhost:" + getPort2() + "/myapp2/products/index.jsp?product_id=1234", out);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // START SNIPPET: e2
                from("jetty:http://localhost:{{port}}/myapp?matchOnUriPrefix=true")
                    .to("jetty:http://localhost:{{port2}}/myapp2?bridgeEndpoint=true&throwExceptionOnFailure=false&urlRewrite=#myRewrite");
                // END SNIPPET: e2

                from("jetty:http://localhost:{{port2}}/myapp2?matchOnUriPrefix=true")
                    .transform().simple("${header.CamelHttpUrl}?${header.CamelHttpQuery}");
            }
        };
    }
}
