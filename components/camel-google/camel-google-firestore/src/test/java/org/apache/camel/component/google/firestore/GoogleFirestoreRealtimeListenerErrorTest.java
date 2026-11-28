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
package org.apache.camel.component.google.firestore;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.EventListener;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreException;
import com.google.cloud.firestore.ListenerRegistration;
import com.google.cloud.firestore.QuerySnapshot;
import io.grpc.Status;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.spi.ExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Errors of the realtime snapshot listener, with a mocked Firestore client: the listener of the SDK does not get any
 * more event after an error.
 */
class GoogleFirestoreRealtimeListenerErrorTest {

    private final AtomicReference<EventListener<QuerySnapshot>> listener = new AtomicReference<>();
    private final ListenerRegistration registration = mock(ListenerRegistration.class);
    private final List<FirestoreException> firstEvents = new ArrayList<>();
    private DefaultCamelContext context;
    private Consumer consumer;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        Firestore firestore = mock(Firestore.class);
        CollectionReference collection = mock(CollectionReference.class);
        when(firestore.collection("users")).thenReturn(collection);
        // the SDK calls the listener on its own thread; here the first event (a snapshot or an error) is delivered at once
        when(collection.addSnapshotListener(any(EventListener.class))).thenAnswer(invocation -> {
            EventListener<QuerySnapshot> eventListener = invocation.getArgument(0);
            listener.set(eventListener);
            eventListener.onEvent(null, firstEvents.isEmpty() ? null : firstEvents.get(0));
            return registration;
        });

        context = new DefaultCamelContext();
        context.getRegistry().bind("firestore", firestore);
        context.start();
    }

    @AfterEach
    void tearDown() {
        context.stop();
    }

    private Consumer startConsumer() throws Exception {
        GoogleFirestoreEndpoint endpoint = context.getEndpoint(
                "google-firestore://users?realtimeUpdates=true&firestoreClient=#firestore", GoogleFirestoreEndpoint.class);
        consumer = endpoint.createConsumer(exchange -> {
        });
        consumer.start();
        return consumer;
    }

    @Test
    void anErrorAsTheFirstEventFailsTheStart() {
        FirestoreException error
                = FirestoreException.forServerRejection(Status.PERMISSION_DENIED, "Missing or insufficient permissions.");
        firstEvents.add(error);

        assertThatThrownBy(this::startConsumer).isSameAs(error);
        verify(registration).remove();
    }

    @Test
    void anErrorAfterTheStartGoesToTheExceptionHandler() throws Exception {
        List<Throwable> handled = new ArrayList<>();
        GoogleFirestoreEndpoint endpoint = context.getEndpoint(
                "google-firestore://users?realtimeUpdates=true&firestoreClient=#firestore", GoogleFirestoreEndpoint.class);
        GoogleFirestoreConsumer realtime = (GoogleFirestoreConsumer) endpoint.createConsumer(exchange -> {
        });
        realtime.setExceptionHandler(new ExceptionHandler() {
            @Override
            public void handleException(Throwable exception) {
                handled.add(exception);
            }

            @Override
            public void handleException(String message, Throwable exception) {
                handled.add(exception);
            }

            @Override
            public void handleException(String message, Exchange exchange, Throwable exception) {
                handled.add(exception);
            }
        });
        consumer = realtime;
        consumer.start();

        FirestoreException error = FirestoreException.forServerRejection(Status.NOT_FOUND, "The database does not exist.");
        listener.get().onEvent(null, error);

        assertThat(handled).containsExactly(error);
    }
}
