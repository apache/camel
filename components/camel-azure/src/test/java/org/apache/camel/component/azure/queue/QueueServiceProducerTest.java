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
package org.apache.camel.component.azure.queue;

import java.util.Iterator;

import com.microsoft.azure.storage.OperationContext;
import com.microsoft.azure.storage.StorageCredentials;
import com.microsoft.azure.storage.StorageCredentialsAccountAndKey;
import com.microsoft.azure.storage.queue.CloudQueue;
import com.microsoft.azure.storage.queue.CloudQueueMessage;
import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.Assert;
import org.junit.Assume;
import org.junit.Test;

public class QueueServiceProducerTest {

    private static final String AZURE_STORAGE_QUEUE = "AZURE_STORAGE_QUEUE";

    @Test
    public void testAppendQueue() throws Exception {

        StorageCredentials creds = getStorageCredentials("camelqueue", System.getenv(AZURE_STORAGE_QUEUE));
        Assume.assumeNotNull("Credentials not null", creds);

        OperationContext.setLoggingEnabledByDefault(true);

        CamelContext camelctx = createCamelContext(creds);
        camelctx.addRoutes(new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:createQueue").to("azure-queue://camelqueue/queue1?credentials=#creds&operation=createQueue");

                from("direct:listQueues").to("azure-queue://camelqueue?credentials=#creds&operation=listQueues");

                from("direct:deleteQueue").to("azure-queue://camelqueue/queue1?credentials=#creds&operation=deleteQueue");

                from("direct:addMessage").to("azure-queue://camelqueue/queue1?credentials=#creds&operation=addMessage");

                from("direct:retrieveMessage").to("azure-queue://camelqueue/queue1?credentials=#creds&operation=retrieveMessage");
            }
        });

        camelctx.start();
        try {
            ProducerTemplate producer = camelctx.createProducerTemplate();

            Iterator<?> it = producer.requestBody("direct:listQueues", null, Iterable.class).iterator();
            Assert.assertFalse("No more queues", it.hasNext());

            producer.sendBody("direct:addMessage", "SomeMsg");

            it = producer.requestBody("direct:listQueues", null, Iterable.class).iterator();
            Assert.assertTrue("Has queues", it.hasNext());
            CloudQueue queue = (CloudQueue)it.next();
            Assert.assertEquals("queue1", queue.getName());
            Assert.assertFalse("No more queues", it.hasNext());

            try {
                CloudQueueMessage msg = producer.requestBody("direct:retrieveMessage", null, CloudQueueMessage.class);
                Assert.assertNotNull("Retrieve a message", msg);
                Assert.assertEquals("SomeMsg", msg.getMessageContentAsString());
            } finally {
                queue.delete();
            }

        } finally {
            camelctx.stop();
        }
    }

    private StorageCredentials getStorageCredentials(String account, String key) {
        return key != null ? new StorageCredentialsAccountAndKey(account, key) : null;
    }

    private CamelContext createCamelContext(StorageCredentials credentials) throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.getRegistry().bind("creds", credentials);
        return context;
    }

}
