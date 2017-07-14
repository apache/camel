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
package org.apache.camel.component.javaspace;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.rmi.RemoteException;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import net.jini.core.entry.Entry;
import net.jini.core.lease.Lease;
import net.jini.core.transaction.CannotAbortException;
import net.jini.core.transaction.CannotCommitException;
import net.jini.core.transaction.Transaction;
import net.jini.core.transaction.UnknownTransactionException;
import net.jini.space.JavaSpace;
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.impl.DefaultConsumer;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.util.ExchangeHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @{link Consumer} implementation for Javaspaces
 * 
 * @version 
 */
public class JavaSpaceConsumer extends DefaultConsumer {

    public static final int READ = 1;
    public static final int TAKE = 0;
    
    @SuppressWarnings("unused")
    private static final Logger LOG = LoggerFactory.getLogger(JavaSpaceConsumer.class);
    
    private final int concurrentConsumers;
    private final boolean transactional;
    private final long transactionTimeout;
    private final String verb;
    private final String templateId;
    private final ScheduledThreadPoolExecutor executor;
    private JavaSpace javaSpace;
    private TransactionHelper transactionHelper;
    
    public JavaSpaceConsumer(final JavaSpaceEndpoint endpoint, Processor processor) throws Exception {
        super(endpoint, processor);
        this.concurrentConsumers = endpoint.getConcurrentConsumers();
        this.transactional = endpoint.isTransactional();
        this.transactionTimeout = endpoint.getTransactionTimeout();
        this.verb = endpoint.getVerb();
        this.templateId = endpoint.getTemplateId();
        this.executor = new ScheduledThreadPoolExecutor(this.concurrentConsumers);
    }

    protected void doStart() throws Exception {
        // TODO: There should be a switch to enable/disable using this security hack
        Utility.setSecurityPolicy("policy.all", "policy_consumer.all");

        int verb = TAKE;
        if (this.verb.equalsIgnoreCase("read")) {
            verb = READ;
        }
        javaSpace = JiniSpaceAccessor.findSpace(((JavaSpaceEndpoint) this.getEndpoint()).getUrl(),
                ((JavaSpaceEndpoint) this.getEndpoint()).getSpaceName());
        if (transactional) {
            transactionHelper = TransactionHelper.getInstance(((JavaSpaceEndpoint) this.getEndpoint()).getUrl());
        }
        for (int i = 0; i < concurrentConsumers; ++i) {
            Task worker = new Task((JavaSpaceEndpoint) this.getEndpoint(), this.getProcessor(), javaSpace,
                    transactionHelper, transactionTimeout, verb, templateId);
            executor.scheduleWithFixedDelay(worker, 0, 1, TimeUnit.NANOSECONDS);
        }

        (new File("policy_consumer.all")).delete();
    }

    @Override
    protected void doStop() throws Exception {
        executor.shutdown();
    }

}

class Task implements Runnable {

    private final JavaSpaceEndpoint endpoint;
    private final Processor processor;
    private final JavaSpace javaSpace;
    private final TransactionHelper transactionHelper;
    private final long transactionTimeout;
    private final int verb;
    private final Entry template;

    Task(JavaSpaceEndpoint endpoint, Processor processor, JavaSpace javaSpace,
            TransactionHelper transactionHelper, long transactionTimeout, int verb, String templateId) throws Exception {
        this.endpoint = endpoint;
        this.processor = processor;
        this.javaSpace = javaSpace;
        this.transactionHelper = transactionHelper;
        this.transactionTimeout = transactionTimeout;
        this.verb = verb;
        if (templateId != null) {
            Entry tmpl = (Entry) this.endpoint.getCamelContext().getRegistry().lookupByName(templateId);
            template = javaSpace.snapshot(tmpl);
        } else {
            this.template = javaSpace.snapshot(new InEntry());
        }
    }

    public void run() {
        Transaction tnx = null;
        try {
            DefaultExchange exchange = (DefaultExchange) endpoint.createExchange(ExchangePattern.InOut);
            Message message = exchange.getIn();
            if (transactionHelper != null) {
                tnx = transactionHelper.getJiniTransaction(transactionTimeout).transaction;
            }
            Entry entry = null;
            switch (verb) {
            case JavaSpaceConsumer.TAKE:
                entry = javaSpace.take(template, tnx, 100);
                break;
            case JavaSpaceConsumer.READ:
                entry = javaSpace.read(template, tnx, 100);
                break;
            default:
                throw new RuntimeCamelException("Wrong verb");
            }
            if (entry != null) {
                if (entry instanceof InEntry) {
                    if (((InEntry) entry).binary) {
                        message.setBody(((InEntry) entry).buffer);
                    } else {
                        ByteArrayInputStream bis = new ByteArrayInputStream(((InEntry) entry).buffer);
                        ObjectInputStream ois = new ObjectInputStream(bis);
                        Object obj = ois.readObject();
                        message.setBody(obj);
                    }
                    processor.process(exchange);
                    Message out = exchange.getOut();
                    if (out.getBody() != null && ExchangeHelper.isOutCapable(exchange)) {
                        OutEntry replyCamelEntry = new OutEntry();
                        replyCamelEntry.correlationId = ((InEntry) entry).correlationId;
                        if (out.getBody() instanceof byte[]) {
                            replyCamelEntry.binary = true;
                            replyCamelEntry.buffer = (byte[]) out.getBody();
                        } else {
                            ByteArrayOutputStream bos = new ByteArrayOutputStream();
                            ObjectOutputStream oos = new ObjectOutputStream(bos);
                            oos.writeObject(out.getBody());
                            replyCamelEntry.binary = false;
                            replyCamelEntry.buffer = bos.toByteArray();
                        }
                        javaSpace.write(replyCamelEntry, tnx, Lease.FOREVER);
                    }
                } else {
                    message.setBody(entry, Entry.class);
                    processor.process(exchange);
                }
            }

        } catch (Exception e) {
            if (tnx != null) {
                try {
                    tnx.abort();
                } catch (UnknownTransactionException e1) {
                    throw new RuntimeCamelException(e1);
                } catch (CannotAbortException e1) {
                    throw new RuntimeCamelException(e1);
                } catch (RemoteException e1) {
                    throw new RuntimeCamelException(e1);
                }
            }
            throw new RuntimeCamelException(e);
        } finally {
            if (tnx != null) {
                try {
                    tnx.commit();
                } catch (UnknownTransactionException e1) {
                    throw new RuntimeCamelException(e1);
                } catch (RemoteException e1) {
                    throw new RuntimeCamelException(e1);
                } catch (CannotCommitException e1) {
                    throw new RuntimeCamelException(e1);
                }
            }
        }
    }

}
