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
package org.apache.camel.component.stomp;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.fusesource.hawtbuf.AsciiBuffer;
import org.fusesource.hawtdispatch.Task;
import org.fusesource.stomp.client.Callback;
import org.fusesource.stomp.client.CallbackConnection;
import org.fusesource.stomp.client.Promise;
import org.fusesource.stomp.client.Stomp;
import org.fusesource.stomp.codec.StompFrame;

import static org.fusesource.hawtbuf.UTF8Buffer.utf8;
import static org.fusesource.stomp.client.Constants.DESTINATION;
import static org.fusesource.stomp.client.Constants.DISCONNECT;
import static org.fusesource.stomp.client.Constants.ID;
import static org.fusesource.stomp.client.Constants.SEND;
import static org.fusesource.stomp.client.Constants.SUBSCRIBE;
import static org.fusesource.stomp.client.Constants.UNSUBSCRIBE;

public class StompEndpoint extends DefaultEndpoint {

    private CallbackConnection connection;
    private StompConfiguration configuration;
    private String destination;
    private Stomp stomp;

    private final List<StompConsumer> consumers = new CopyOnWriteArrayList<StompConsumer>();

    public StompEndpoint(String uri, StompComponent component, StompConfiguration configuration, String destination) {
        super(uri, component);
        this.configuration = configuration;
        this.destination = destination;
    }

    public Producer createProducer() throws Exception {
        return new StompProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        return new StompConsumer(this, processor);
    }

    public boolean isSingleton() {
        return true;
    }

    @Override
    protected void doStart() throws Exception {
        final Promise<CallbackConnection> promise = new Promise<CallbackConnection>();

        stomp = new Stomp(configuration.getBrokerURL());
        stomp.setLogin(configuration.getLogin());
        stomp.setPasscode(configuration.getPasscode());
        stomp.connectCallback(promise);

        connection = promise.await();

        connection.getDispatchQueue().execute(new Task() {
            @Override
            public void run() {
                connection.receive(new Callback<StompFrame>() {
                    @Override
                    public void onFailure(Throwable value) {
                        if (started.get()) {
                            connection.close(null);
                        }
                    }

                    @Override
                    public void onSuccess(StompFrame value) {
                        if (!consumers.isEmpty()) {
                            Exchange exchange = createExchange();
                            exchange.getIn().setBody(value.content());
                            for (StompConsumer consumer : consumers) {
                                consumer.processExchange(exchange);
                            }
                        }
                    }
                });
                connection.resume();
            }
        });
    }

    @Override
    protected void doStop() throws Exception {
        connection.getDispatchQueue().execute(new Task() {
            @Override
            public void run() {
                StompFrame frame = new StompFrame(DISCONNECT);
                connection.send(frame, null);
            }
        });
        connection.close(null);
    }

    protected void send(final Exchange exchange, final AsyncCallback callback) {
        final StompFrame frame = new StompFrame(SEND);
        frame.addHeader(DESTINATION, StompFrame.encodeHeader(destination));
        frame.content(utf8(exchange.getIn().getBody().toString()));

        connection.getDispatchQueue().execute(new Task() {
            @Override
            public void run() {
                connection.send(frame, new Callback<Void>() {
                    @Override
                    public void onFailure(Throwable e) {
                        exchange.setException(e);
                        callback.done(false);
                    }

                    @Override
                    public void onSuccess(Void v) {
                        callback.done(false);
                    }
                });
            }
        });
    }

    void addConsumer(final StompConsumer consumer) {
        connection.getDispatchQueue().execute(new Task() {
            @Override
            public void run() {
                StompFrame frame = new StompFrame(SUBSCRIBE);
                frame.addHeader(DESTINATION, StompFrame.encodeHeader(destination));
                frame.addHeader(ID, consumer.id);
                connection.send(frame, null);
            }
        });
        consumers.add(consumer);
    }

    void removeConsumer(final StompConsumer consumer) {
        connection.getDispatchQueue().execute(new Task() {
            @Override
            public void run() {
                StompFrame frame = new StompFrame(UNSUBSCRIBE);
                frame.addHeader(DESTINATION, StompFrame.encodeHeader(destination));
                frame.addHeader(ID, consumer.id);
                connection.send(frame, null);
            }
        });
        consumers.remove(consumer);
    }

    AsciiBuffer getNextId() {
        return connection.nextId();
    }
}
