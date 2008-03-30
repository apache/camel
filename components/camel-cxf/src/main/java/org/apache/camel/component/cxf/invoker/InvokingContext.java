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
package org.apache.camel.component.cxf.invoker;

import java.util.Map;

import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.Message;

public interface InvokingContext {

    /**
     * This method is called when the router is preparing an outbound message
     * (orignated from the router's client) to be sent to the target CXF server.
     * It sets the content in the given (out) message object.
     */
    void setRequestOutMessageContent(Message message, Map<Class, Object> contents);

    /**
     * This method is call when the CxfClient receives a response from a CXF server and needs
     * to extract the response object from the message.
     */
    Object getResponseObject(Exchange exchange, Map<String, Object> responseContext);

    /**
     * This method is called when the routing interceptor has received a response message
     * from the target CXF server and needs to set the response in the outgoing message
     * that is to be sent to the client.
     */
    void setResponseContent(Message outMessage, Object resultPayload);

    /**
     * This method is called when the routing interceptor has intercepted a message from
     * the client and needs to extract the request content from the message.  It retreives
     * and receives the request content from the incoming message.
     */
    Map<Class, Object> getRequestContent(Message inMessage);

}
