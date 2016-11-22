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

import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.firebase.exception.DatabaseErrorException;
import org.apache.camel.impl.DefaultAsyncProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The producer, which can be used to set a value for a specific key in Firebase.
 */
public class FirebaseProducer extends DefaultAsyncProducer {

    private static final Logger LOG = LoggerFactory.getLogger(FirebaseProducer.class);
    private final String rootReference;
    private final FirebaseEndpoint endpoint;

    public FirebaseProducer(FirebaseEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
        rootReference = endpoint.getRootReference();
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        final Message in = exchange.getIn();
        final Message out = exchange.getOut();
        String firebaseKey = (String) in.getHeader(endpoint.getKeyName());
        Object value = in.getBody();
        DatabaseReference ref = FirebaseDatabase
                .getInstance(endpoint.getFirebaseApp())
                .getReference(rootReference).child(firebaseKey);
        final boolean reply = endpoint.isReply();
        out.setHeaders(in.getHeaders());
        if (reply) { // Wait for reply
            processReply(exchange, callback, value, ref);
        } else { // Fire and forget
            ref.setValue(value);
            out.setBody(in.getBody());
            callback.done(true);
        }
        return !reply;
    }

    private void processReply(Exchange exchange, AsyncCallback callback, Object value, DatabaseReference ref) {
        ref.setValue(value, (DatabaseError databaseError, DatabaseReference databaseReference) -> {
            if (databaseError != null) {
                exchange.setException(new DatabaseErrorException(databaseError));
            } else {
                exchange.getOut().setBody(databaseReference);
            }
            callback.done(false);
        });
    }
}
