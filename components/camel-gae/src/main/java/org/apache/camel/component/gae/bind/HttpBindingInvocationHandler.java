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
package org.apache.camel.component.gae.bind;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.http.common.HttpBinding;
import org.apache.camel.http.common.HttpMessage;

/**
 * Post-processes {@link HttpBinding} invocations by delegating to an
 * endpoint's {@link InboundBinding}.
 */
public class HttpBindingInvocationHandler<E extends Endpoint, S, T> implements InvocationHandler {

    private E endpoint;
    private HttpBinding httpBinding;
    private InboundBinding<E, S, T> inboundBinding; 
    
    public HttpBindingInvocationHandler(E endpoint, HttpBinding httpBinding, InboundBinding<E, S, T> inboundBinding) {
        this.endpoint = endpoint;
        this.httpBinding = httpBinding;
        this.inboundBinding = inboundBinding;
    }
    
    @SuppressWarnings("unchecked")
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        Object result = method.invoke(httpBinding, args); // updates args
        if (method.getName().equals("readRequest") && (args.length == 2)) {
            HttpMessage message = (HttpMessage)args[1];
            // prepare exchange for further inbound binding operations
            message.getExchange().setIn(message);
            // delegate further request binding operations to inbound binding
            inboundBinding.readRequest(endpoint, message.getExchange(), (S)args[0]);
        } else if (method.getName().equals("writeResponse") && (args.length == 2)) {
            // delegate further response binding operations to inbound binding
            inboundBinding.writeResponse(endpoint, (Exchange)args[0], (T)args[1]);
        }
        return result;
    }
    
}
