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

import java.io.File;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Producer;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

public class FromSmbRenameReadLockIT extends SmbServerTestSupport {

    @Override
    public void doPostSetup() throws Exception {
        prepareSmbServer();
    }

    protected String getSmbPollingUrl() {
        return String.format(
                "smb2:%s/%s?username=%s&password=%s&path=/renamerl&delete=true&delay=1000&initialDelay=1500&readLock=rename",
                service.address(), service.shareName(), service.userName(), service.password());
    }

    protected String getSmbUrl() {
        return String.format(
                "smb2:%s/%s?username=%s&password=%s&path=/renamerl",
                service.address(), service.shareName(), service.userName(), service.password());
    }

    @Test
    public void testFromFileToSmb() throws Exception {
        // verify binary file written to smb dir
        await().atMost(3, TimeUnit.SECONDS)
                .untilAsserted(() -> assertNotNull((copyFileContentFromContainer("/data/rw/renamerl/logo.jpeg"))));

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        MockEndpoint.assertIsSatisfied(context);

        // verify binary file removed during processing
        await().atMost(3, TimeUnit.SECONDS)
                .untilAsserted(() -> assertNull((copyFileContentFromContainer("/data/rw/renamerl/logo.jpeg"))));
    }

    private void prepareSmbServer() throws Exception {
        // write binary file to smb dir
        Endpoint endpoint = context.getEndpoint(getSmbUrl());
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setBody(new File("src/test/data/smbbinarytest/logo.jpeg"));
        exchange.getIn().setHeader(Exchange.FILE_NAME, "logo.jpeg");
        Producer producer = endpoint.createProducer();
        producer.start();
        producer.process(exchange);
        producer.stop();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(getSmbPollingUrl()).to("mock:result");
            }
        };
    }
}
