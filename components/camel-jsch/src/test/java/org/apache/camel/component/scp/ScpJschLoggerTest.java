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
package org.apache.camel.component.scp;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.Logger;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The component logs the JSch messages of its sessions with a logger of each JSch instance, and leaves the static
 * logger of JSch, which applies to every user of JSch in the JVM, as it is.
 */
public class ScpJschLoggerTest extends ScpServerTestSupport {

    public ScpJschLoggerTest() {
        super(false);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("file:" + getScpPath() + "?recursive=true&delete=true")
                        .convertBodyTo(String.class)
                        .to("mock:result");
            }
        };
    }

    @Test
    public void testStaticJschLoggerIsKept() throws Exception {
        assumeTrue(this.isSetupComplete());

        Logger before = JSch.getLogger();
        Logger application = new Logger() {
            @Override
            public boolean isEnabled(int level) {
                return false;
            }

            @Override
            public void log(int level, String message) {
                // noop
            }
        };
        JSch.setLogger(application);
        try {
            getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");

            String uri = getScpUri() + "?username=admin&password=admin&useUserKnownHostsFile=false";
            template.sendBodyAndHeader(uri, "Hello World", Exchange.FILE_NAME, "hello.txt");

            MockEndpoint.assertIsSatisfied(context);
            assertSame(application, JSch.getLogger());
        } finally {
            JSch.setLogger(before);
        }
    }
}
