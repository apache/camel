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
package org.apache.camel.component.beanstalk;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

import com.surftools.BeanstalkClient.BeanstalkException;
import com.surftools.BeanstalkClient.Client;
import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.component.beanstalk.processors.Command;
import org.apache.camel.support.DefaultAsyncProducer;

public class BeanstalkProducer extends DefaultAsyncProducer {
    private ExecutorService executor;
    private Client client;
    private final Command command;

    public BeanstalkProducer(BeanstalkEndpoint endpoint, final Command command) throws Exception {
        super(endpoint);
        this.command = command;
    }

    public Command getCommand() {
        return command;
    }

    @Override
    public void process(final Exchange exchange) throws Exception {
        Future f = executor.submit(new RunCommand(exchange));
        f.get();
    }

    @Override
    public boolean process(final Exchange exchange, final AsyncCallback callback) {
        try {
            executor.submit(new RunCommand(exchange, callback));
        } catch (Throwable t) {
            exchange.setException(t);
            callback.done(true);
            return true;
        }
        return false;
    }

    protected void resetClient() {
        closeClient();
        initClient();
    }

    protected void closeClient() {
        if (client != null) {
            client.close();
        }
    }

    protected void initClient() {
        this.client = getEndpoint().getConnection().newWritingClient();
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();
        executor = getEndpoint().getCamelContext().getExecutorServiceManager().newSingleThreadExecutor(this, "Beanstalk-Producer");
        executor.execute(() -> initClient());
    }

    @Override
    protected void doStop() throws Exception {
        getEndpoint().getCamelContext().getExecutorServiceManager().shutdown(executor);
        closeClient();
        super.doStop();
    }

    @Override
    public BeanstalkEndpoint getEndpoint() {
        return (BeanstalkEndpoint) super.getEndpoint();
    }

    class RunCommand implements Runnable {
        private final Exchange exchange;
        private final AsyncCallback callback;

        RunCommand(final Exchange exchange) {
            this(exchange, null);
        }

        RunCommand(final Exchange exchange, final AsyncCallback callback) {
            this.exchange = exchange;
            this.callback = callback;
        }

        @Override
        public void run() {
            try {
                try {
                    command.act(client, exchange);
                } catch (BeanstalkException e) {
                    /* Retry one time */
                    resetClient();
                    command.act(client, exchange);
                }
            } catch (Throwable t) {
                exchange.setException(t);
            } finally {
                if (callback != null) {
                    callback.done(false);
                }
            }
        }
    }
}
