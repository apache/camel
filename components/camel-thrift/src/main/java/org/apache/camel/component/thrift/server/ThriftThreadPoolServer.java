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
package org.apache.camel.component.thrift.server;

import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.thrift.TException;
import org.apache.thrift.TProcessor;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.server.ServerContext;
import org.apache.thrift.server.TServer;
import org.apache.thrift.server.TServerEventHandler;
import org.apache.thrift.transport.TSaslTransportException;
import org.apache.thrift.transport.TServerTransport;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/*
 * Thrift ThreadPoolServer implementation with executors controlled by the Camel Executor Service Manager
 */
public class ThriftThreadPoolServer extends TServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ThriftThreadPoolServer.class.getName());

    public static class Args extends AbstractServerArgs<Args> {
        private ExecutorService executorService;
        private ExecutorService startThreadPool;
        private CamelContext context;

        private int requestTimeout = 20;
        private TimeUnit requestTimeoutUnit = TimeUnit.SECONDS;
        private int beBackoffSlotLength = 100;
        private TimeUnit beBackoffSlotLengthUnit = TimeUnit.MILLISECONDS;

        public Args(TServerTransport transport) {
            super(transport);
        }

        public Args requestTimeout(int n) {
            requestTimeout = n;
            return this;
        }

        public Args requestTimeoutUnit(TimeUnit tu) {
            requestTimeoutUnit = tu;
            return this;
        }

        // Binary exponential backoff slot length
        public Args beBackoffSlotLength(int n) {
            beBackoffSlotLength = n;
            return this;
        }

        // Binary exponential backoff slot time unit
        public Args beBackoffSlotLengthUnit(TimeUnit tu) {
            beBackoffSlotLengthUnit = tu;
            return this;
        }

        public Args executorService(ExecutorService executorService) {
            this.executorService = executorService;
            return this;
        }

        public Args startThreadPool(ExecutorService startThreadPool) {
            this.startThreadPool = startThreadPool;
            return this;
        }

        public Args context(CamelContext context) {
            this.context = context;
            return this;
        }
    }

    // Executor service for handling client connections
    private final ExecutorService invoker;
    private final CamelContext context;
    private final ExecutorService startExecutor;

    private final TimeUnit requestTimeoutUnit;

    private final long requestTimeout;

    private final long beBackoffSlotInMillis;

    private Random random = new Random(System.currentTimeMillis());

    public ThriftThreadPoolServer(Args args) {
        super(args);

        requestTimeoutUnit = args.requestTimeoutUnit;
        requestTimeout = args.requestTimeout;
        beBackoffSlotInMillis = args.beBackoffSlotLengthUnit.toMillis(args.beBackoffSlotLength);

        context = args.context;
        invoker = args.executorService;
        startExecutor = args.startThreadPool;
    }

    public void serve() {
        try {
            serverTransport_.listen();
        } catch (TTransportException ttx) {
            LOGGER.error("Error occurred during listening.", ttx);
            return;
        }

        // Run the preServe event
        if (eventHandler_ != null) {
            eventHandler_.preServe();
        }

        startExecutor.execute(() -> {
            stopped_ = false;
            setServing(true);
            
            waitForShutdown();
            
            context.getExecutorServiceManager().shutdownGraceful(invoker);
            setServing(false);
        });
    }

    public void waitForShutdown() {
        int failureCount = 0;
        while (!stopped_) {
            try {
                TTransport client = serverTransport_.accept();
                WorkerProcess wp = new WorkerProcess(client);

                int retryCount = 0;
                long remainTimeInMillis = requestTimeoutUnit.toMillis(requestTimeout);
                while (true) {
                    try {
                        invoker.execute(wp);
                        break;
                    } catch (Throwable t) {
                        if (t instanceof RejectedExecutionException) {
                            retryCount++;
                            try {
                                if (remainTimeInMillis > 0) {
                                    // do a truncated 20 binary exponential
                                    // backoff sleep
                                    long sleepTimeInMillis = ((long)(random.nextDouble() * (1L << Math.min(retryCount, 20)))) * beBackoffSlotInMillis;
                                    sleepTimeInMillis = Math.min(sleepTimeInMillis, remainTimeInMillis);
                                    TimeUnit.MILLISECONDS.sleep(sleepTimeInMillis);
                                    remainTimeInMillis = remainTimeInMillis - sleepTimeInMillis;
                                } else {
                                    client.close();
                                    wp = null;
                                    LOGGER.warn("Task has been rejected by ExecutorService " + retryCount + " times till timedout, reason: " + t);
                                    break;
                                }
                            } catch (InterruptedException e) {
                                LOGGER.warn("Interrupted while waiting to place client on executor queue.");
                                Thread.currentThread().interrupt();
                                break;
                            }
                        } else if (t instanceof Error) {
                            LOGGER.error("ExecutorService threw error: " + t, t);
                            throw (Error)t;
                        } else {
                            // for other possible runtime errors from
                            // ExecutorService, should also not kill serve
                            LOGGER.warn("ExecutorService threw error: " + t, t);
                            break;
                        }
                    }
                }
            } catch (TTransportException ttx) {
                if (!stopped_) {
                    ++failureCount;
                    LOGGER.warn("Transport error occurred during acceptance of message.", ttx);
                }
            }
        }
    }

    public void stop() {
        stopped_ = true;
        serverTransport_.interrupt();
        context.getExecutorServiceManager().shutdownGraceful(startExecutor);
    }

    private final class WorkerProcess implements Runnable {

        /**
         * Client that this services.
         */
        private TTransport client;

        /**
         * Default constructor.
         *
         * @param client Transport to process
         */
        private WorkerProcess(TTransport client) {
            this.client = client;
        }

        /**
         * Loops on processing a client forever
         */
        public void run() {
            TProcessor processor = null;
            TTransport inputTransport = null;
            TTransport outputTransport = null;
            TProtocol inputProtocol = null;
            TProtocol outputProtocol = null;

            TServerEventHandler eventHandler = null;
            ServerContext connectionContext = null;

            try {
                processor = processorFactory_.getProcessor(client);
                inputTransport = inputTransportFactory_.getTransport(client);
                outputTransport = outputTransportFactory_.getTransport(client);
                inputProtocol = inputProtocolFactory_.getProtocol(inputTransport);
                outputProtocol = outputProtocolFactory_.getProtocol(outputTransport);

                eventHandler = getEventHandler();
                if (eventHandler != null) {
                    connectionContext = eventHandler.createContext(inputProtocol, outputProtocol);
                }
                // we check stopped_ first to make sure we're not supposed to be
                // shutting
                // down. this is necessary for graceful shutdown.
                while (true) {

                    if (eventHandler != null) {
                        eventHandler.processContext(connectionContext, inputTransport, outputTransport);
                    }

                    if (stopped_ || !processor.process(inputProtocol, outputProtocol)) {
                        break;
                    }
                }
            } catch (TSaslTransportException ttx) {
                // Something thats not SASL was in the stream, continue silently
            } catch (TTransportException ttx) {
                // Assume the client died and continue silently
            } catch (TException tx) {
                LOGGER.error("Thrift error occurred during processing of message.", tx);
            } catch (Exception x) {
                LOGGER.error("Error occurred during processing of message.", x);
            } finally {
                if (eventHandler != null) {
                    eventHandler.deleteContext(connectionContext, inputProtocol, outputProtocol);
                }
                if (inputTransport != null) {
                    inputTransport.close();
                }
                if (outputTransport != null) {
                    outputTransport.close();
                }
                if (client.isOpen()) {
                    client.close();
                }
            }
        }
    }
}
