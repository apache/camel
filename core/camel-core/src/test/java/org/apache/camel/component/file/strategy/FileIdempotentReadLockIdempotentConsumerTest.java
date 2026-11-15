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
package org.apache.camel.component.file.strategy;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.processor.idempotent.MemoryIdempotentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An idempotent consumer (idempotent=true, or noop=true) with an idempotent read lock: both use the same idempotent
 * repository, and the file is consumed once.
 */
class FileIdempotentReadLockIdempotentConsumerTest extends ContextTestSupport {

    private final MemoryIdempotentRepository myRepo = new MemoryIdempotentRepository();

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Override
    protected Registry createCamelRegistry() throws Exception {
        Registry jndi = super.createCamelRegistry();
        jndi.bind("myRepo", myRepo);
        return jndi;
    }

    @ParameterizedTest
    @ValueSource(strings = { "idempotent", "idempotent-changed", "idempotent-rename" })
    void noopConsumesTheFileOnce(String readLock) throws Exception {
        startRoute("noop=true&readLock=" + readLock);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");
        // the file stays, and the next polls skip it
        mock.setAssertPeriod(500);

        template.sendBodyAndHeader(fileUri(), "Hello World", Exchange.FILE_NAME, "hello.txt");

        mock.assertIsSatisfied();
        assertThat(myRepo.contains(testFile("hello.txt").toAbsolutePath().toString())).isTrue();
    }

    @Test
    void idempotentConsumesTheFileOnce() throws Exception {
        startRoute("idempotent=true&readLock=idempotent");

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        template.sendBodyAndHeader(fileUri(), "Hello World", Exchange.FILE_NAME, "hello.txt");

        mock.assertIsSatisfied();

        // a file with the same name is skipped as it was consumed before
        mock.reset();
        mock.expectedMessageCount(0);
        mock.setAssertPeriod(500);

        template.sendBodyAndHeader(fileUri(), "Hello Again", Exchange.FILE_NAME, "hello.txt");

        mock.assertIsSatisfied();
        assertThat(testFile("hello.txt")).exists();
    }

    private void startRoute(String options) throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(fileUri("?initialDelay=0&delay=10&readLockCheckInterval=50&idempotentRepository=#myRepo&"
                             + options))
                        .to("mock:result");
            }
        });
        context.start();
    }
}
