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

import org.apache.cxf.Bus;
import org.apache.cxf.endpoint.EndpointImpl;
import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.Message;
import org.apache.cxf.phase.PhaseInterceptorChain;

public interface InvokingContext {
    /**
     * This method is called when an request from a (routing) client is observed
     * at the router's transport (inbound to the router from a client).  It will 
     * return an "in" interceptor chain that will allow the appropriate routing 
     * interceptor to receive and handle the message.
     * @param exchange
     * @return in interceptor chain
     */
    PhaseInterceptorChain getRequestInInterceptorChain(Exchange exchange);

    /**
     * This method is called when the router is preparing an outbound message 
     * (orignated from the router's client) to be sent to the target CXF server.
     * It sets the content in the given (out) message object.
     * @param content
     */
    void setRequestOutMessageContent(Message message, Object content);

    /**
     * This method is called when a response from a CXF server is observed at the
     * router's transport (inbound to the router from a server).  It will return an
     * "in" interceptor chain that will allow the response to be returned to the 
     * involved routing interceptor (with the appropriate interceptors in between).
     * @param exchange
     * @return in interceptor chain
     */
    PhaseInterceptorChain getResponseInInterceptorChain(Exchange exchange);

    /**
     * This method is called when the router is ready to forward a request from a client
     * to the target CXF server.  It returns an "out" intercetptor chain that will deliver 
     * the request message to the CXF server.
     * @param exchange
     * @return out interceptor chain
     */
    PhaseInterceptorChain getRequestOutInterceptorChain(Exchange exchange);

    /**
     * This method is called when the router is ready to forward a response from a CXF
     * to the client who has made the request. It returns an "out" interceptor chain that 
     * will deliver the response message to the client.
     * @param exchange
     * @return out interceptor chain
     */
    PhaseInterceptorChain getResponseOutInterceptorChain(Exchange exchange);

    /**
     * This method is call when the CxfClient receives a response from a CXF server and needs
     * to extract the response object from the message.
     * @param exchange
     * @param responseContext
     * @return response object
     */
    Object getResponseObject(Exchange exchange, Map<String, Object> responseContext);
    
    /**
     * This method is called to set the fault observers on the endpoint that are specified
     * to the phases meaningful to the routing context.
     * @param endpointImpl
     * @param bus
     */
    void setEndpointFaultObservers(EndpointImpl endpointImpl, Bus bus);

    /**
     * This method is called when the routing interceptor has received a response message
     * from the target CXF server and needs to set the response in the outgoing message
     * that is to be sent to the client.
     * @param outMessage
     * @param resultPayload
     */
    void setResponseContent(Message outMessage, Object resultPayload);
    
    /**
     * This method is called when the routing interceptor has intercepted a message from
     * the client and needs to extract the request content from the message.  It retreives
     * and receives the request content from the incoming message. 
     * @param inMessage
     * @return the request from client
     */
    Object getRequestContent(Message inMessage);

}
