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

import org.apache.camel.CamelExecutionException;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.GenericFileOperationFailedException;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Without a known hosts file (useUserKnownHostsFile=false and no knownHostsFile), strictHostKeyChecking decides.
 */
public class ScpNoKnownHostsTest extends ScpServerTestSupport {

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
    public void testNoKnownHostsWithoutStrictHostKeyChecking() throws Exception {
        assumeTrue(this.isSetupComplete());

        getMockEndpoint("mock:result").expectedBodiesReceived("Hello World");

        String uri = getScpUri() + "?username=admin&password=admin&useUserKnownHostsFile=false";
        template.sendBodyAndHeader(uri, "Hello World", Exchange.FILE_NAME, "hello.txt");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testNoKnownHostsWithStrictHostKeyChecking() {
        assumeTrue(this.isSetupComplete());

        String uri = getScpUri() + "?username=admin&password=admin&useUserKnownHostsFile=false"
                     + "&strictHostKeyChecking=yes";
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.sendBodyAndHeader(uri, "Hello World", Exchange.FILE_NAME, "hello.txt"));
        // the host key is rejected as the host is not known, so the producer cannot connect
        GenericFileOperationFailedException cause = assertInstanceOf(GenericFileOperationFailedException.class, e.getCause());
        assertTrue(cause.getMessage().startsWith("Failed to connect to"), cause.getMessage());
    }
}
