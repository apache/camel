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

import org.apache.camel.BindToRegistry;
import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.support.processor.idempotent.MemoryIdempotentRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SmbConsumerIdempotentMemoryRefIT extends SmbServerTestSupport {

    private MemoryIdempotentRepository repo;

    protected String getSmbUrl() {
        return String.format(
                "smb2:%s/%s?username=%s&password=%s&path=/idem&idempotent=true"
                             + "&idempotentRepository=#myConsumerIdemRepo&idempotentKey=${file:onlyname}&delete=true",
                service.address(), service.shareName(), service.userName(), service.password());
    }

    @BindToRegistry("myConsumerIdemRepo")
    public MemoryIdempotentRepository addRepo() {
        repo = new MemoryIdempotentRepository();
        repo.setCacheSize(5);
        repo.start();
        return repo;
    }

    @Test
    public void testIdempotent() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(5).create();
        getMockEndpoint("mock:result").expectedMessageCount(5);

        sendFile(getSmbUrl(), "Hello A", "a.txt");
        sendFile(getSmbUrl(), "Hello B", "b.txt");
        sendFile(getSmbUrl(), "Hello C", "c.txt");
        sendFile(getSmbUrl(), "Hello D", "d.txt");
        sendFile(getSmbUrl(), "Hello E", "e.txt");

        MockEndpoint.assertIsSatisfied(context);
        assertTrue(notify.matchesWaitTime());

        assertEquals(5, repo.getCache().size());
        assertTrue(repo.contains("a.txt"));
        assertTrue(repo.contains("b.txt"));
        assertTrue(repo.contains("c.txt"));
        assertTrue(repo.contains("d.txt"));
        assertTrue(repo.contains("e.txt"));

        MockEndpoint.resetMocks(context);
        notify = new NotifyBuilder(context).whenDone(2).create();

        getMockEndpoint("mock:result").expectedMessageCount(2);

        // duplicate
        sendFile(getSmbUrl(), "Hello A", "a.txt");
        sendFile(getSmbUrl(), "Hello B", "b.txt");
        // new files
        sendFile(getSmbUrl(), "Hello F", "f.txt");
        sendFile(getSmbUrl(), "Hello G", "g.txt");

        MockEndpoint.assertIsSatisfied(context);
        assertTrue(notify.matchesWaitTime());

        assertEquals(5, repo.getCache().size());
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {

            public void configure() {
                from(getSmbUrl())
                        .to("mock:result");
            }
        };
    }
}
