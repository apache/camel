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

import java.util.concurrent.TimeUnit;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.Registry;
import org.apache.camel.support.processor.idempotent.MemoryIdempotentRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

class FileIdempotentChangedReadLockRemoveOnCommitTest extends ContextTestSupport {

    final MemoryIdempotentRepository myRepo = new MemoryIdempotentRepository();

    @Override
    protected Registry createCamelRegistry() throws Exception {
        Registry jndi = super.createCamelRegistry();
        jndi.bind("myRepo", myRepo);
        return jndi;
    }

    @Test
    void testExistingEntryNotRemovedOnCommitFalse() throws Exception {
        assertThat(myRepo.getCacheSize()).isZero();

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(1);

        // drop hello.txt -> processed once, moved to .camel, entry retained
        template.sendBodyAndHeader(fileUri(), "Hello World", Exchange.FILE_NAME, "hello.txt");
        mock.assertIsSatisfied();

        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> assertThat(myRepo.contains("hello.txt")).isTrue());

        // re-drop a file with the same name -> must be skipped as a duplicate
        // and the existing entry must be retained because readLockRemoveOnCommit=false
        mock.reset();
        mock.expectedMessageCount(0);
        template.sendBodyAndHeader(fileUri(), "Hello World Again", Exchange.FILE_NAME, "hello.txt");

        // give the consumer several poll cycles to (wrongly) process/remove it
        mock.assertIsSatisfied(2000);

        assertThat(myRepo.contains("hello.txt"))
                .as("existing idempotent entry must be retained when readLockRemoveOnCommit=false")
                .isTrue();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from(fileUri("?initialDelay=0&delay=50&readLockCheckInterval=50"
                             + "&readLock=idempotent-changed&idempotentRepository=#myRepo"
                             + "&idempotentKey=${file:onlyname}&readLockRemoveOnCommit=false"))
                        .to("mock:result");
            }
        };
    }
}
