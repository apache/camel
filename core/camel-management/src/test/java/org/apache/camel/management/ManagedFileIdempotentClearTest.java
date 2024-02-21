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
package org.apache.camel.management;

import java.io.File;
import java.util.Set;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.Endpoint;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.support.processor.idempotent.FileIdempotentRepository;
import org.apache.camel.util.FileUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisabledOnOs(OS.AIX)
public class ManagedFileIdempotentClearTest extends ManagementTestSupport {

    private IdempotentRepository repo;

    @Test
    public void testDuplicateMessagesAreFilteredOut() throws Exception {
        Endpoint startEndpoint = resolveMandatoryEndpoint("direct:start");
        MockEndpoint resultEndpoint = getMockEndpoint("mock:result");

        MBeanServer mbeanServer = getMBeanServer();

        // services
        Set<ObjectName> names = mbeanServer.queryNames(new ObjectName("org.apache.camel" + ":type=services,*"), null);
        ObjectName on = null;
        for (ObjectName name : names) {
            if (name.toString().contains("FileIdempotentRepository")) {
                on = name;
                break;
            }
        }

        assertTrue(mbeanServer.isRegistered(on), "Should be registered");
        String path = (String) mbeanServer.getAttribute(on, "FilePath");
        assertEquals(FileUtil.normalizePath(testFile("idempotentfilestore.dat").toString()), FileUtil.normalizePath(path));

        Integer size = (Integer) mbeanServer.getAttribute(on, "CacheSize");
        assertEquals(1, size.intValue());

        assertFalse(repo.contains("1"));
        assertFalse(repo.contains("2"));
        assertFalse(repo.contains("3"));
        assertTrue(repo.contains("4"));

        resultEndpoint.expectedBodiesReceived("one", "two", "three");

        sendMessage(startEndpoint, "1", "one");
        sendMessage(startEndpoint, "2", "two");
        sendMessage(startEndpoint, "3", "three");

        resultEndpoint.assertIsSatisfied();

        assertTrue(repo.contains("1"));
        assertTrue(repo.contains("2"));
        assertTrue(repo.contains("3"));
        assertTrue(repo.contains("4"));

        size = (Integer) mbeanServer.getAttribute(on, "CacheSize");
        assertEquals(4, size.intValue());

        // clear
        mbeanServer.invoke(on, "clear", null, null);

        // there should be 0 now
        size = (Integer) mbeanServer.getAttribute(on, "CacheSize");
        assertEquals(0, size.intValue());

        assertFalse(repo.contains("1"));
        assertFalse(repo.contains("2"));
        assertFalse(repo.contains("3"));
        assertFalse(repo.contains("4"));
    }

    protected void sendMessage(final Endpoint startEndpoint, final Object messageId, final Object body) {
        template.send(startEndpoint, exchange -> {
            // now lets fire in a message
            Message in = exchange.getIn();
            in.setBody(body);
            in.setHeader("messageId", messageId);
        });
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        File store = testFile("idempotentfilestore.dat").toFile();
        return new RouteBuilder() {
            public void configure() {
                repo = FileIdempotentRepository.fileIdempotentRepository(store);
                // let's add 4 to start with
                repo.add("4");

                from("direct:start")
                        .idempotentConsumer(header("messageId"), repo)
                        .to("mock:result");
            }
        };
    }
}
