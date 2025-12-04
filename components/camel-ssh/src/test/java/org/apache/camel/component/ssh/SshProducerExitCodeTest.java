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

package org.apache.camel.component.ssh;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

public class SshProducerExitCodeTest extends SshComponentTestSupport {

    @Override
    public void doPreSetup() throws Exception {
        super.doPreSetup();
        sshd.setCommandFactory(new ExitCodeCommandFactory());
    }

    @Test
    public void testExitCode() throws Exception {
        MockEndpoint mockEndpoint = getMockEndpoint("mock:result");
        mockEndpoint.expectedHeaderReceived(SshConstants.EXIT_VALUE, 2);

        Map<String, Object> headers = new HashMap<>();
        headers.put(SshConstants.USERNAME_HEADER, "smx");
        headers.put(SshConstants.PASSWORD_HEADER, "smx");

        template.sendBodyAndHeaders("direct:start", "Hello", headers);

        mockEndpoint.assertIsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                // 1st try (expected: ExitValue=1)
                from("direct:start")
                        .setBody()
                        .simple("exit 1")
                        .to("ssh:localhost:" + port)
                        .log("1st try: ExitValue=${header.CamelSshExitValue}")

                        // 2nd try (expected: ExitValue=2)
                        .setBody()
                        .simple("exit 2")
                        .to("ssh:localhost:" + port)
                        .log("2nd try: ExitValue=${header.CamelSshExitValue}")
                        .to("mock:result");
            }
        };
    }
}
