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

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import static junit.framework.TestCase.fail;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.firebase.data.FirebaseMessage;
import org.apache.camel.component.firebase.data.Operation;
import org.apache.camel.component.firebase.provider.ConfigurationProvider;
import org.apache.camel.component.firebase.provider.SampleInputProvider;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;


/**
 * Starts a route which listens to the remove event in Firebase. It then writes and deletes an entry in Firebase and
 * asserts, if the entry was deleted or not.
 */
public class FirebaseConsumerDeleteTest {

    private final ReentrantLock reentrantLock = new ReentrantLock();

    private final Condition wake = reentrantLock.newCondition();

    @Test
    public void whenDeleteDeleteMessageShouldBeIntercepted() throws Exception {
        CamelContext context = new DefaultCamelContext();
        boolean[] deleteMessageReceived = {false};
        FirebaseConfig firebaseConfig = ConfigurationProvider.createDemoConfig();
        createAndDeleteContent(firebaseConfig, false);
        setupRoute(context, deleteMessageReceived);

        context.start();
        try {
            reentrantLock.lock();
            wake.await(30, TimeUnit.SECONDS);
        } finally {
            reentrantLock.unlock();
        }
        assertThat(deleteMessageReceived[0]).isTrue();
        context.stop();
    }

    private void createAndDeleteContent(FirebaseConfig firebaseConfig, boolean delete) {
        final DatabaseReference rootReference = FirebaseDatabase.getInstance(firebaseConfig.getFirebaseApp())
                .getReference(ConfigurationProvider.createRootReference()).child(SampleInputProvider.createDeleteKey());
        rootReference
                .setValue("AETHELWULF 839-856", (databaseError, databaseReference) -> {
                    if (delete) {
                        databaseReference.removeValue();
                    }
                });
    }

    private void setupRoute(CamelContext context, final boolean[] deleteMessageReceived) throws Exception {
        boolean deleteFired[] = {false};
        context.addRoutes(new RouteBuilder() {
            public void configure() {
                try {
                    from(String.format("firebase://%s?rootReference=%s&serviceAccountFile=%s",
                            ConfigurationProvider.createDatabaseUrl(), ConfigurationProvider.createRootReference(), ConfigurationProvider.createFirebaseConfigLink()))
                            .to("log:firebasetest?level=WARN")
                            .process(exchange -> {
                                FirebaseMessage firebaseMessage = (FirebaseMessage) exchange.getIn().getBody();
                                if (firebaseMessage.getOperation() == Operation.CHILD_REMOVED) {
                                    deleteMessageReceived[0] = true;
                                    try {
                                        reentrantLock.lock();
                                        wake.signal();
                                    } finally {
                                        reentrantLock.unlock();
                                    }
                                } else {
                                    if (!deleteFired[0]) {
                                        deleteFired[0] = true;
                                        FirebaseConfig firebaseConfig = ConfigurationProvider.createDemoConfig();
                                        createAndDeleteContent(firebaseConfig, true);
                                    }
                                }
                            });

                } catch (Exception e) {
                    fail(e.toString());
                }
            }
        });
    }
}
