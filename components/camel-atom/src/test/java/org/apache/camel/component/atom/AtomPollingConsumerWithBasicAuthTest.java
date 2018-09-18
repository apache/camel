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
package org.apache.camel.component.atom;
import org.apache.camel.builder.RouteBuilder;
import org.junit.AfterClass;
import org.junit.BeforeClass;

public class AtomPollingConsumerWithBasicAuthTest extends AtomPollingConsumerTest {

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("atom:http://localhost:" + JettyTestServer.getInstance().port + "/?splitEntries=false&username=camel&password=camelPass")
                        .to("mock:result");

                // this is a bit weird syntax that normally is not used using the feedUri parameter
                from("atom:?feedUri=http://localhost:" + JettyTestServer.getInstance().port + "/&splitEntries=false&username=camel&password=camelPass")
                        .to("mock:result2");
            }
        };
    }

    @BeforeClass
    public static void startServer() {
        JettyTestServer.getInstance().startServer();
    }

    @AfterClass
    public static void stopServer() {
        JettyTestServer.getInstance().stopServer();
    }

}
