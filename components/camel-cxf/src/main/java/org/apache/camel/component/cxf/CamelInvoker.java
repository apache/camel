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
package org.apache.camel.component.cxf;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import org.apache.cxf.frontend.MethodDispatcher;
import org.apache.cxf.helpers.CastUtils;
import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.MessageContentsList;
import org.apache.cxf.service.Service;
import org.apache.cxf.service.invoker.AbstractInvoker;
import org.apache.cxf.service.invoker.Invoker;
import org.apache.cxf.service.model.BindingOperationInfo;

public class CamelInvoker implements Invoker  {
    private CxfConsumer cxfConsumer;
    public CamelInvoker(CxfConsumer consumer) {
        cxfConsumer = consumer;
    }

    public Object invoke(Exchange exchange, Object o) {
        BindingOperationInfo bop = exchange.get(BindingOperationInfo.class);
        MethodDispatcher md = (MethodDispatcher) 
            exchange.get(Service.class).get(MethodDispatcher.class.getName());
        Method m = md.getMethod(bop);
        
        List<Object> params = null;
        if (o instanceof List) {
            params = CastUtils.cast((List<?>)o);
        } else if (o != null) {
            params = new MessageContentsList(o);
        }
        
        final List<Object> messageBody = new ArrayList<Object>(params.size()+1);
        messageBody.add(m.getName());
        for (Object obj: params) {
            messageBody.add(obj);
        }  
        
        CxfEndpoint endpoint = (CxfEndpoint) cxfConsumer.getEndpoint();
        CxfExchange cxfExchange = endpoint.createExchange(exchange.getInMessage());
        cxfExchange.getIn().setBody(messageBody);
        
        
        try {
            cxfConsumer.getProcessor().process(cxfExchange);
        } catch (Exception e) {
            // catch the exception and send back to cxf client
            e.printStackTrace();
        }
        System.out.println(cxfExchange.getOut().getBody());
        //TODO deal with the paraments that contains holders
        Object[] result = (Object[])cxfExchange.getOut().getBody();
        return result;
        
    }

}
