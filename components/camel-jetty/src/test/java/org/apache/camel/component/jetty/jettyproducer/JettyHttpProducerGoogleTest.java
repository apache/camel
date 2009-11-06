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
package org.apache.camel.component.jetty.jettyproducer;

import java.util.concurrent.Future;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * @version $Revision$
 */
public class JettyHttpProducerGoogleTest extends CamelTestSupport {

    @Test
    public void testGoogleFrontPage() throws Exception {
        String reply = template.requestBody("direct:start", null, String.class);
        assertNotNull(reply);
    }

    @Test
    public void testGoogleFrontPageFutureTask() throws Exception {
        Object body = null;
        Future<String> reply = (Future<String>) template.requestBody("direct:start", body);
        assertNotNull(reply);
        assertNotNull(reply.get());
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                // to prevent redirect being thrown as an exception
                from("direct:start").to("jetty://http://www.google.com?throwExceptionOnFailure=false");
            }
        };
    }
}

