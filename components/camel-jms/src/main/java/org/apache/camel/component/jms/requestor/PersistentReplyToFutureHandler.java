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
package org.apache.camel.component.jms.requestor;

import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import javax.jms.ExceptionListener;
import javax.jms.Message;
import javax.jms.Session;

import org.apache.camel.component.jms.JmsConfiguration;
import org.apache.camel.component.jms.JmsProducer;
import org.apache.camel.component.jms.requestor.DeferredRequestReplyMap.DeferredMessageSentCallback;
import org.apache.camel.component.jms.requestor.PersistentReplyToRequestor.MessageSelectorComposer;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.core.task.TaskExecutor;
import org.springframework.jms.listener.AbstractMessageListenerContainer;
import org.springframework.jms.listener.DefaultMessageListenerContainer;
import org.springframework.transaction.PlatformTransactionManager;

public class PersistentReplyToFutureHandler extends FutureHandler {

    private static final transient Log LOG = LogFactory.getLog(PersistentReplyToFutureHandler.class);
    protected PersistentReplyToRequestor requestor;
    protected DeferredMessageSentCallback callback;
    protected String correlationID;

    public PersistentReplyToFutureHandler(PersistentReplyToRequestor requestor,
                                          String correlationID) {
        super();
        this.requestor = requestor;
        this.correlationID = correlationID;
    }

    public PersistentReplyToFutureHandler(PersistentReplyToRequestor requestor,
                                          DeferredMessageSentCallback callback) {
        super();
        this.requestor = requestor;
        this.callback = callback;
    }

    @Override
    public Message get() throws InterruptedException, ExecutionException {
        Message result = null;
        try {
            updateSelector();
            result = super.get();
        } finally {
            revertSelector();
        }
        return result;
    }

    @Override
    public Message get(long timeout, TimeUnit unit) throws InterruptedException,
                                                           ExecutionException,
                                                           TimeoutException {
        Message result = null;
        try {
            updateSelector();
            result = super.get(timeout, unit);
        } finally {
            revertSelector();
        }
        return result;
    }

    protected void updateSelector() throws ExecutionException {
        try {
            MessageSelectorComposer composer = (MessageSelectorComposer)requestor.getListenerContainer();
            composer.addCorrelationID((correlationID != null) ? correlationID : callback.getMessage().getJMSMessageID());
        } catch (Exception e) {
            throw new ExecutionException(e);
        }
    }

    protected void revertSelector() throws ExecutionException {
        try {
            MessageSelectorComposer composer = (MessageSelectorComposer)requestor.getListenerContainer();
            composer.removeCorrelationID((correlationID != null) ? correlationID : callback.getMessage().getJMSMessageID());
        } catch (Exception e) {
            throw new ExecutionException(e);
        }
    }
}
