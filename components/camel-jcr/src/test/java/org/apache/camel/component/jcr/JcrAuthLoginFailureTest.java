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
package org.apache.camel.component.jcr;

import javax.jcr.LoginException;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.junit.Test;

public class JcrAuthLoginFailureTest extends JcrAuthTestBase {

    @Test
    public void testCreateNodeWithAuthentication() throws Exception {
        Exchange exchange = createExchangeWithBody("<message>hello!</message>");
        Exchange out = template.send("direct:a", exchange);
        assertNotNull(out);
        String uuid = out.getOut().getBody(String.class);
        assertNull("Expected body to be null, found JCR node UUID", uuid);
        assertTrue("Wrong exception type", out.getException() instanceof LoginException);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // START SNIPPET: jcr
                from("direct:a").setHeader(JcrConstants.JCR_NODE_NAME,
                        constant("node")).setHeader("my.contents.property",
                        body()).to(
                        "jcr://not-a-user:nonexisting-password@repository" + BASE_REPO_PATH);
                // END SNIPPET: jcr
            }
        };
    }
}
