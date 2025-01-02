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
package org.apache.camel.component.smb2;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.GenericFileExist;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

public class SmbConsumerIdempotentKeyChangedIssueIT extends SmbServerTestSupport {

    private Endpoint endpoint;

    protected String getSmbUrl() {
        return String.format(
                "smb2:%s/%s?username=%s&password=%s&path=/idempotentkey&readLock=changed&idempotent=true"
                             + "&idempotentKey=${file:onlyname}-${file:size}",
                service.address(), service.shareName(), service.userName(), service.password());
    }

    @Test
    public void testIdempotent() throws Exception {
        NotifyBuilder oneExchangeDone = new NotifyBuilder(context).whenDone(1).create();
        getMockEndpoint("mock:file").expectedBodiesReceived("Hello World");

        template.sendBodyAndHeader(endpoint, "Hello World", Exchange.FILE_NAME, "hello.txt");
        MockEndpoint.assertIsSatisfied(context);

        oneExchangeDone.matches(5, TimeUnit.SECONDS);

        MockEndpoint.resetMocks(context);
        getMockEndpoint("mock:file").expectedBodiesReceived("Hello World Again");

        // TODO remove 'file-exists'
        template.sendBodyAndHeaders(endpoint, "Hello World Again", Map.of(Exchange.FILE_NAME, "hello.txt",
                Smb2Constants.SMB_FILE_EXISTS, GenericFileExist.Override.name()));

        MockEndpoint.assertIsSatisfied(context);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                endpoint = endpoint(getSmbUrl());
                from(endpoint).convertBodyTo(String.class).to("log:file").to("mock:file");
            }
        };
    }
}
