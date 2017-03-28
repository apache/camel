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
package org.apache.camel.component.file.remote;

import org.apache.camel.builder.NotifyBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.processor.idempotent.MemoryIdempotentRepository;
import org.junit.Test;

/**
 * Memory repo test
 */
public class FtpConsumerIdempotentMemoryRefTest extends FtpServerTestSupport {

    private MemoryIdempotentRepository repo;

    private String getFtpUrl() {
        return "ftp://admin@localhost:" + getPort()
                + "/idempotent?password=admin&binary=false&idempotent=true&idempotentRepository=#myRepo&idempotentKey=${file:onlyname}&delete=true";
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry jndi = super.createRegistry();
        repo = new MemoryIdempotentRepository();
        repo.setCacheSize(5);
        jndi.bind("myRepo", repo);
        return jndi;
    }

    @Test
    public void testIdempotent() throws Exception {
        NotifyBuilder notify = new NotifyBuilder(context).whenDone(5).create();
        getMockEndpoint("mock:result").expectedMessageCount(5);

        sendFile(getFtpUrl(), "Hello A", "a.txt");
        sendFile(getFtpUrl(), "Hello B", "b.txt");
        sendFile(getFtpUrl(), "Hello C", "c.txt");
        sendFile(getFtpUrl(), "Hello D", "d.txt");
        sendFile(getFtpUrl(), "Hello E", "e.txt");

        assertMockEndpointsSatisfied();
        assertTrue(notify.matchesMockWaitTime());

        assertEquals(5, repo.getCache().size());
        assertTrue(repo.contains("a.txt"));
        assertTrue(repo.contains("b.txt"));
        assertTrue(repo.contains("c.txt"));
        assertTrue(repo.contains("d.txt"));
        assertTrue(repo.contains("e.txt"));

        resetMocks();
        notify = new NotifyBuilder(context).whenDone(2).create();

        getMockEndpoint("mock:result").expectedMessageCount(2);

        // duplicate
        sendFile(getFtpUrl(), "Hello A", "a.txt");
        sendFile(getFtpUrl(), "Hello B", "b.txt");
        // new files
        sendFile(getFtpUrl(), "Hello F", "f.txt");
        sendFile(getFtpUrl(), "Hello G", "g.txt");

        assertMockEndpointsSatisfied();
        assertTrue(notify.matchesMockWaitTime());

        assertEquals(5, repo.getCache().size());
    }
    
    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from(getFtpUrl())
                    .to("log:result")
                    .to("mock:result");
            }
        };
    }
}