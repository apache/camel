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
package org.apache.camel.management;

import java.io.File;
import java.util.Set;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.idempotent.FileIdempotentRepository;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.util.FileUtil;

/**
 * @version 
 */
public class ManagedFileIdempotentConsumerTest extends ManagementTestSupport {
    protected Endpoint startEndpoint;
    protected MockEndpoint resultEndpoint;
    private File store = new File("target/idempotentfilestore.dat");
    private IdempotentRepository<String> repo;

    public void testDuplicateMessagesAreFilteredOut() throws Exception {
        // JMX tests dont work well on AIX CI servers (hangs them)
        if (isPlatform("aix")) {
            return;
        }

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

        assertTrue("Should be registered", mbeanServer.isRegistered(on));
        String path = (String) mbeanServer.getAttribute(on, "FilePath");
        assertEquals(FileUtil.normalizePath("target/idempotentfilestore.dat"), FileUtil.normalizePath(path));

        Integer size = (Integer) mbeanServer.getAttribute(on, "CacheSize");
        assertEquals(1, size.intValue());

        assertFalse(repo.contains("1"));
        assertFalse(repo.contains("2"));
        assertFalse(repo.contains("3"));
        assertTrue(repo.contains("4"));

        resultEndpoint.expectedBodiesReceived("one", "two", "three");

        sendMessage("1", "one");
        sendMessage("2", "two");
        sendMessage("1", "one");
        sendMessage("2", "two");
        sendMessage("4", "four");
        sendMessage("1", "one");
        sendMessage("3", "three");

        resultEndpoint.assertIsSatisfied();

        assertTrue(repo.contains("1"));
        assertTrue(repo.contains("2"));
        assertTrue(repo.contains("3"));
        assertTrue(repo.contains("4"));

        size = (Integer) mbeanServer.getAttribute(on, "CacheSize");
        assertEquals(4, size.intValue());

        // remove one from repo
        mbeanServer.invoke(on, "remove", new Object[]{"1"}, new String[]{"java.lang.String"});

        // reset
        mbeanServer.invoke(on, "reset", null, null);

        // there should be 3 now
        size = (Integer) mbeanServer.getAttribute(on, "CacheSize");
        assertEquals(3, size.intValue());

        assertFalse(repo.contains("1"));
        assertTrue(repo.contains("2"));
        assertTrue(repo.contains("3"));
        assertTrue(repo.contains("4"));
    }

    protected void sendMessage(final Object messageId, final Object body) {
        template.send(startEndpoint, new Processor() {
            public void process(Exchange exchange) {
                // now lets fire in a message
                Message in = exchange.getIn();
                in.setBody(body);
                in.setHeader("messageId", messageId);
            }
        });
    }

    @Override
    protected void setUp() throws Exception {
        // delete file store before testing
        if (store.exists()) {
            store.delete();
        }

        repo = FileIdempotentRepository.fileIdempotentRepository(store);

        // let's add 4 to start with
        repo.add("4");

        super.setUp();
        startEndpoint = resolveMandatoryEndpoint("direct:start");
        resultEndpoint = getMockEndpoint("mock:result");
    }

    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from("direct:start")
                    .idempotentConsumer(header("messageId"), repo)
                    .to("mock:result");
            }
        };
    }
}