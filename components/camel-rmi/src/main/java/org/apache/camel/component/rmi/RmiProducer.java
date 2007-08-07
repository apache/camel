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
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.rmi.registry.Registry;

import org.apache.camel.Exchange;
import org.apache.camel.component.bean.BeanProcessor;
import org.apache.camel.impl.DefaultProducer;

/**
 * @version $Revision: 533076 $
 */
public class RmiProducer extends DefaultProducer {

    private final RmiEndpoint endpoint;
    private Remote remote;
    private BeanProcessor beanProcessor;

    public RmiProducer(RmiEndpoint endpoint) throws RemoteException, NotBoundException {
        super(endpoint);
        this.endpoint = endpoint;
    }

    public void process(Exchange exchange) throws Exception {
        if (beanProcessor == null) {
            beanProcessor = new BeanProcessor(getRemote(), getEndpoint().getContext());
        }
        beanProcessor.process(exchange);
    }

    public Remote getRemote() throws RemoteException, NotBoundException {
        if (remote == null) {
            Registry registry = endpoint.getRegistry();
            remote = registry.lookup(endpoint.getName());
        }
        return remote;
    }

}
