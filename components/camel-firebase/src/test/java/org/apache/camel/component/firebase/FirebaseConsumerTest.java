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
package org.apache.camel.component.firebase;


import java.io.IOException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import com.google.firebase.database.FirebaseDatabase;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.firebase.provider.ConfigurationProvider;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

/**
 * Writes a dummy message and then checks, if the consumer receives at least one message.
 */
public class FirebaseConsumerTest extends CamelTestSupport {

    private final ReentrantLock lock = new ReentrantLock();

    private final Condition wake = lock.newCondition();

    @Test
    public void whenFirebaseListenerShouldReceiveMessages() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);
        assertMockEndpointsSatisfied();
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        final String databaseUrl = "gil-sample-app.firebaseio.com";
        final String originalRootReference = "server/saving-data";
        String serviceAccountFile = ConfigurationProvider.createFirebaseConfigLink();
        String rootReference = URLEncoder.encode(originalRootReference, "UTF-8");
        insertDummyData(String.format("https://%s", databaseUrl), originalRootReference, serviceAccountFile);

        return new RouteBuilder() {
            public void configure() {
                try {
                    from(String.format("firebase://" + databaseUrl + "?rootReference=%s&serviceAccountFile=%s",
                            rootReference, serviceAccountFile))
                            .to("log:firebasetest?level=WARN")
                            .to("mock:result");
                } catch (Exception e) {
                    fail(e.toString());
                }
            }
        };
    }

    private void insertDummyData(String databaseUrl, String originalRootReference, String serviceAccountFile) throws IOException, InterruptedException {
        FirebaseConfig config = new FirebaseConfig.Builder(databaseUrl, originalRootReference, URLDecoder.decode(serviceAccountFile, "UTF-8"))
                .build();
        config.init();
        FirebaseDatabase
                .getInstance(config.getFirebaseApp())
                .getReference(config.getRootReference()).child("dummy").setValue("test", (databaseError, databaseReference) -> {
                    try {
                        lock.lock();
                        wake.signal();
                    } finally {
                        lock.unlock();
                    }
                });
        try {
            lock.lock();
            wake.await();
        } finally {
            lock.unlock();
        }
    }
}