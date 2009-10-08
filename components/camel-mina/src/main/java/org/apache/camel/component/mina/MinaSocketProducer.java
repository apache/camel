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
package org.apache.camel.component.mina;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelExchangeException;
import org.apache.camel.Exchange;
import org.apache.camel.ExchangeTimedOutException;
import org.apache.camel.Producer;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.impl.DefaultProducer;
import org.apache.camel.util.ExchangeHelper;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.mina.common.ConnectFuture;
import org.apache.mina.common.IoHandlerAdapter;
import org.apache.mina.common.IoSession;
import org.apache.mina.transport.socket.nio.SocketConnector;

/**
 * A reentrant multi-threaded socket {@link Producer} implementation for MINA.
 */
public class MinaSocketProducer extends DefaultProducer<MinaExchange> {
    private static final transient Log LOG = LogFactory.getLog(MinaSocketProducer.class);

    private final MinaEndpoint endpoint;
    private final BlockingQueue<IoSession> sessions;
    private final ExecutorService executor;
    
    private final boolean sync;
    private final long timeout;
    private final String charset;
    private final SocketConnector connector;

    public MinaSocketProducer(MinaEndpoint endpoint) {
        super(endpoint);

        this.endpoint = endpoint;
        int poolSize = endpoint.getConfiguration().getProducerPoolSize();
        this.executor = Executors.newFixedThreadPool(poolSize);
        this.sessions = new ArrayBlockingQueue<IoSession>(poolSize);

        this.sync = endpoint.getConfiguration().isSync();
        this.timeout = endpoint.getConfiguration().getTimeout();
        this.charset = endpoint.getConfiguration().getCharsetName();
        this.connector = (SocketConnector) endpoint.getConnector();
    }

    public void process(Exchange exchange) throws Exception {
        Future<?> result = executor.submit(new MinaProducerWorker(exchange));
        try {
            result.get();
        } catch (ExecutionException e) {
            // unwrap the exception occurred in the worker thread
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeCamelException) {
                cause = cause.getCause();
            }
            throw (Exception) cause; 
        }
    }

    @Override
    protected void doStop() throws Exception {
        // Change the worker timeout to 0 second to make the I/O thread quit soon when 
        // there is no connection to manage.  Default worker timeout is 60 sec. and 
        // therefore the client using MinaProducer can not terminate the JVM asap but must  
        // wait for the timeout to happen, so to speed this up we set the timeout to 0.
        connector.setWorkerTimeout(0);
        
        executor.shutdown();
        
        for (int i = 0; i < sessions.size(); ++i) {
            IoSession session = sessions.take();
            if (session.isConnected()) {
                session.close();
            }
        }
    }

    
    /**
     * Worker thread implementation for MINA socket producer. 
     */
    private final class MinaProducerWorker implements Runnable {
        private final Exchange exchange;
        private CountDownLatch latch;
        
        public MinaProducerWorker(Exchange exchange) {
            this.exchange = exchange;
        }
        
        public void countDown() {
            if (latch != null) {
                latch.countDown();
            }
        }
        
        public void run() {
            Object body = MinaPayloadHelper.getIn(endpoint, exchange);
            if (body == null) {
                LOG.warn("No payload to send for exchange: " + exchange);
                return;
            }

            // get an existing session or create a new one
            ResponseHandler handler;
            IoSession session = sessions.poll();
            if ((session != null) && session.isConnected()) {
                handler = (ResponseHandler) session.getHandler();
                handler.reset(this);
            } else {
                handler = new ResponseHandler(this);
                ConnectFuture future = connector.connect(
                        endpoint.getAddress(), 
                        handler, 
                        endpoint.getConnectorConfig());
                future.join();
                session = future.getSession();
            }

            // set the exchange encoding property
            if (charset != null) {
                exchange.setProperty(Exchange.CHARSET_NAME, charset);
            }

            try {
                if (sync) {
                    latch = new CountDownLatch(1);
                }
                
                MinaHelper.writeBody(session, body, exchange);
                
                if (sync) {
                    // wait for response, consider timeout
                    latch.await(timeout, TimeUnit.MILLISECONDS);
                    if (latch.getCount() == 1) {
                        throw new ExchangeTimedOutException(exchange, timeout);
                    }
    
                    // analyze response
                    if (handler.getCause() != null) {
                        session.close();
                        throw new CamelExchangeException("Exception in response handler", exchange, handler.getCause());
                    } else if (!handler.isMessageReceived()) {
                        session.close();
                        throw new CamelExchangeException("No response received from remote server: " + endpoint.getEndpointUri(), exchange);
                    } else {
                        // set the result on either IN or OUT on the original exchange depending on its pattern
                        if (ExchangeHelper.isOutCapable(exchange)) {
                            MinaPayloadHelper.setOut(exchange, handler.getMessage());
                        } else {
                            MinaPayloadHelper.setIn(exchange, handler.getMessage());
                        }                    
                    }
                }
            } catch (Exception e) {
                throw new RuntimeCamelException(e);
            } finally {
                // make the session available in the pool
                sessions.add(session);
            }
        }
    }    
    
    
    private final class ResponseHandler extends IoHandlerAdapter {
        private MinaProducerWorker worker;
        
        private Object message;
        private Throwable cause;
        private boolean messageReceived;
        
        private ResponseHandler(MinaProducerWorker worker) {
            reset(worker);
        }

        public void reset(MinaProducerWorker worker) {
            message = null;
            cause = null;
            messageReceived = false;
            this.worker = worker;
        }

        @Override
        public void messageReceived(IoSession session, Object obj) throws Exception {
            message = obj;
            cause = null;
            messageReceived = true;
            worker.countDown();
        }

        @Override
        public void sessionClosed(IoSession session) throws Exception {
            if (message == null) {
                LOG.debug("Session closed but no message received from address: " + endpoint.getAddress());
                worker.countDown();
            }
        }

        @Override
        public void exceptionCaught(IoSession session, Throwable throwable) {
            LOG.error("Exception on receiving message from address: " + endpoint.getAddress(), cause);
            message = null;
            cause = throwable;
            messageReceived = false;
            if (session != null) {
                session.close();
            }
        }

        public Throwable getCause() {
            return cause;
        }

        public Object getMessage() {
            return message;
        }

        public boolean isMessageReceived() {
            return messageReceived;
        }
    }

}
