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

import com.google.firebase.database.ChildEventListener;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.FirebaseDatabase;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.component.firebase.data.FirebaseMessage;
import org.apache.camel.component.firebase.data.Operation;
import org.apache.camel.component.firebase.exception.FirebaseException;
import org.apache.camel.impl.DefaultConsumer;

/**
 * Listens to child events of the root reference and forwards the incoming message on the route.
 */
public class FirebaseConsumer extends DefaultConsumer {

    private final FirebaseConfig firebaseConfig;

    private final FirebaseEndpoint endpoint;

    public FirebaseConsumer(FirebaseEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
        firebaseConfig = endpoint.getFirebaseConfig();
    }

    @Override
    protected void doStart() throws Exception {
        FirebaseDatabase
                .getInstance(endpoint.getFirebaseApp())
                .getReference(firebaseConfig.getRootReference())
                .addChildEventListener(new ChildEventListener() {
                    @Override
                    public void onChildAdded(DataSnapshot dataSnapshot, String s) {
                        forwardMessage(new FirebaseMessage.Builder(Operation.CHILD_ADD, dataSnapshot)
                                .setPreviousChildName(s).build());
                    }

                    @Override
                    public void onChildChanged(DataSnapshot dataSnapshot, String s) {
                        forwardMessage(new FirebaseMessage.Builder(Operation.CHILD_CHANGED, dataSnapshot)
                                .setPreviousChildName(s).build());
                    }

                    @Override
                    public void onChildRemoved(DataSnapshot dataSnapshot) {
                        forwardMessage(new FirebaseMessage.Builder(Operation.CHILD_REMOVED, dataSnapshot).build());
                    }

                    @Override
                    public void onChildMoved(DataSnapshot dataSnapshot, String s) {
                        forwardMessage(new FirebaseMessage.Builder(Operation.CHILD_MOVED, dataSnapshot)
                                .setPreviousChildName(s).build());
                    }

                    @Override
                    public void onCancelled(DatabaseError databaseError) {
                        forwardMessage(new FirebaseMessage.Builder(Operation.CANCELLED).setDatabaseError(databaseError)
                                .build());
                    }
                });
    }

    private void forwardMessage(FirebaseMessage o) {
        Exchange exchange = endpoint.createExchange();
        exchange.getIn().setBody(o);

        try {
            // send message to next processor in the route
            getProcessor().process(exchange);
        } catch (Exception e) {
            throw new FirebaseException("Message forwarding failed", e);
        } finally {
            // log exception if an exception occurred and was not handled
            if (exchange.getException() != null) {
                getExceptionHandler().handleException("Error processing exchange", exchange, exchange.getException());
            }
        }
    }
}
