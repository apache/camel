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
package org.apache.camel.component.cxf.feature;

import java.util.concurrent.Executor;

import org.apache.camel.component.cxf.MessageInvoker;
import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.AbstractPhaseInterceptor;
import org.apache.cxf.phase.Phase;
import org.apache.cxf.service.Service;

/**
 * This interceptor just works for invoking the MessageInvoker implementor
 *
 */
public class MessageInvokerInterceptor extends AbstractPhaseInterceptor<Message> {


    public MessageInvokerInterceptor() {
        super(Phase.INVOKE);
    }

    public void handleMessage(final Message message) {
        final Exchange exchange = message.getExchange();
        final Endpoint endpoint = exchange.get(Endpoint.class);
        final Service service = endpoint.getService();
        final MessageInvoker invoker = (MessageInvoker)service.getInvoker();

        // How to deal with the oneway messge
        Runnable invocation = new Runnable() {

            public void run() {
                Exchange runableEx = message.getExchange();
                invoker.invoke(runableEx);
                if (!exchange.isOneWay()) {
                    Endpoint ep = exchange.get(Endpoint.class);
                    Message outMessage = runableEx.getOutMessage();
                    copyJaxwsProperties(message, outMessage);
                    if (outMessage == null) {
                        outMessage = ep.getBinding().createMessage();
                        exchange.setOutMessage(outMessage);
                    }
                }
            }

        };

        Executor executor = getExecutor(endpoint);
        if (exchange.get(Executor.class) == executor) {
            // already executing on the appropriate executor
            invocation.run();
        } else {
            exchange.put(Executor.class, executor);
            executor.execute(invocation);
        }
    }


    /**
     * Get the Executor for this invocation.
     * @param endpoint
     * @return
     */
    private Executor getExecutor(final Endpoint endpoint) {
        return endpoint.getService().getExecutor();
    }

    private void copyJaxwsProperties(Message inMsg, Message outMsg) {
        outMsg.put(Message.WSDL_OPERATION, inMsg.get(Message.WSDL_OPERATION));
        outMsg.put(Message.WSDL_SERVICE, inMsg.get(Message.WSDL_SERVICE));
        outMsg.put(Message.WSDL_INTERFACE, inMsg.get(Message.WSDL_INTERFACE));
        outMsg.put(Message.WSDL_PORT, inMsg.get(Message.WSDL_PORT));
        outMsg.put(Message.WSDL_DESCRIPTION, inMsg.get(Message.WSDL_DESCRIPTION));
    }

}



