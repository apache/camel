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
package org.apache.camel.component.rmi;

import java.rmi.NotBoundException;
import java.rmi.RemoteException;

import org.apache.camel.Exchange;
import org.apache.camel.component.bean.BeanHolder;
import org.apache.camel.component.bean.BeanProcessor;
import org.apache.camel.impl.DefaultProducer;

/**
 * @version 
 */
public class RmiProducer extends DefaultProducer {

    private BeanProcessor beanProcessor;

    public RmiProducer(RmiEndpoint endpoint) throws RemoteException, NotBoundException {
        super(endpoint);
        BeanHolder holder = new RmiRegistryBean(endpoint.getCamelContext(), endpoint.getName(), endpoint.getRegistry());
        beanProcessor = new BeanProcessor(holder);
        String method = endpoint.getMethod();
        if (method != null) {
            beanProcessor.setMethod(method);
        }
    }

    public void process(Exchange exchange) throws Exception {
        beanProcessor.process(exchange);
    }

}
