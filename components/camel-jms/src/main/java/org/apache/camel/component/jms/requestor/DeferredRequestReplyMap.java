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

import java.util.concurrent.FutureTask;

import javax.jms.JMSException;
import javax.jms.Message;

import org.apache.camel.component.jms.JmsConfiguration.MessageSentCallback;
import org.apache.camel.component.jms.JmsProducer;
import org.apache.camel.util.TimeoutMap;
import org.apache.camel.util.UuidGenerator;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

public class DeferredRequestReplyMap  {
    private static final transient Log LOG = LogFactory.getLog(DeferredRequestReplyMap.class);
    private Requestor requestor;
    private JmsProducer producer;
    private TimeoutMap deferredRequestMap;
    private TimeoutMap deferredReplyMap;

    public static class DeferredMessageSentCallback implements MessageSentCallback {
        private DeferredRequestReplyMap map;
        private String transitionalID;
        private Message message;
        private Object monitor;

        public DeferredMessageSentCallback(DeferredRequestReplyMap map, UuidGenerator uuidGenerator, Object monitor) {
            transitionalID = uuidGenerator.generateId();
            this.map = map;
            this.monitor = monitor;
        }

        public DeferredRequestReplyMap getDeferredRequestReplyMap() {
            return map;
        }

        public String getID() {
            return transitionalID;
        }

        public Message getMessage() {
            return message;
        }
        
        public void sent(Message message) {
            this.message = message;
            map.processDeferredReplies(monitor, getID(), message);
        }
    }

    public DeferredRequestReplyMap(Requestor requestor,
                                   JmsProducer producer,
                                   TimeoutMap deferredRequestMap,
                                   TimeoutMap deferredReplyMap) {
        this.requestor = requestor;
        this.producer = producer;
        this.deferredRequestMap = deferredRequestMap;
        this.deferredReplyMap = deferredReplyMap;
    }

    public long getRequestTimeout() {
        return producer.getRequestTimeout();
    }

    public DeferredMessageSentCallback createDeferredMessageSentCallback() {
        return new DeferredMessageSentCallback(this, getUuidGenerator(), requestor);
    }

    public void put(DeferredMessageSentCallback callback, FutureTask futureTask) {
        deferredRequestMap.put(callback.getID(), futureTask, getRequestTimeout());
    }

    public void processDeferredRequests(String correlationID, Message inMessage) {
        processDeferredRequests(requestor, deferredRequestMap, deferredReplyMap,
                                correlationID, requestor.getMaxRequestTimeout(), inMessage);
    }

    public static void processDeferredRequests(Object monitor,
                                               TimeoutMap requestMap,
                                               TimeoutMap replyMap,
                                               String correlationID,
                                               long timeout,
                                               Message inMessage) {
        synchronized (monitor) {
            try {
                Object handler = requestMap.get(correlationID);
                if (handler == null) {
                    if (requestMap.size() > replyMap.size()) {
                        replyMap.put(correlationID, inMessage, timeout);
                    } else {
                        LOG.warn("Response received for unknown correlationID: " + correlationID + "; response: " + inMessage);
                    }
                }
                if (handler != null && handler instanceof ReplyHandler) {
                    ReplyHandler replyHandler = (ReplyHandler) handler;
                    boolean complete = replyHandler.handle(inMessage);
                    if (complete) {
                        requestMap.remove(correlationID);
                    }
                }
            } catch (JMSException e) {
                throw new FailedToProcessResponse(inMessage, e);
            }
        }
    }

    public void processDeferredReplies(Object monitor, String transitionalID, Message outMessage) {
        synchronized (monitor) {
            try {
                Object handler = deferredRequestMap.get(transitionalID);
                if (handler == null) {
                    return;
                }
                deferredRequestMap.remove(transitionalID);
                String correlationID = outMessage.getJMSMessageID();
                Object in = deferredReplyMap.get(correlationID);

                if (in != null && in instanceof Message) {
                    Message inMessage = (Message)in;
                    if (handler instanceof ReplyHandler) {
                        ReplyHandler replyHandler = (ReplyHandler)handler;
                        try {
                            boolean complete = replyHandler.handle(inMessage);
                            if (complete) {
                                deferredReplyMap.remove(correlationID);
                            }
                        } catch (JMSException e) {
                            throw new FailedToProcessResponse(inMessage, e);
                        }
                    }
                } else {
                    deferredRequestMap.put(correlationID, handler, getRequestTimeout());
                }
            } catch (JMSException e) {
                throw new FailedToProcessResponse(outMessage, e);
            }
        }
    }

    protected UuidGenerator getUuidGenerator() {
        return producer.getUuidGenerator();
    }
}
