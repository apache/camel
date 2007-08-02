/*
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
package org.apache.camel.component.pojo;

import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.impl.DefaultProducer;

/**
 * Represents a pojo endpoint that uses reflection
 * to send messages around.
 *
 * @version $Revision: 519973 $
 */
public class PojoEndpoint extends DefaultEndpoint<PojoExchange> {
    private final String pojoName;
    private Object pojo;

    public PojoEndpoint(String uri, Component component, String pojoName) {
        super(uri, component);
        this.pojoName = pojoName;
    }

    public PojoComponent getPojoComponent() {
        return (PojoComponent) super.getComponent();
    }

    public Producer<PojoExchange> createProducer() throws Exception {
        final Object pojo = getPojo();
        if (pojo == null) {
            throw new NoPojoAvailableException(this);
        }

        return new DefaultProducer(this) {
            public void process(Exchange exchange) {
                PojoExchange pojoExchange = toExchangeType(exchange);
                invoke(pojo, pojoExchange);
                exchange.copyFrom(pojoExchange);
            }
        };
    }

    public Consumer<PojoExchange> createConsumer(Processor processor) throws Exception {
        throw new Exception("You cannot consume from pojo endpoints.");
    }

    /**
     * This causes us to invoke the endpoint Pojo using reflection.
     *
     * @param pojo
     */
    public static void invoke(Object pojo, PojoExchange exchange) {
        PojoInvocation invocation = exchange.getInvocation();
        invocation.invoke(pojo, exchange);
    }

    public PojoExchange createExchange() {
        return new PojoExchange(getContext());
    }

    public boolean isSingleton() {
        return true;
    }

    public Object getPojo() {
        if (pojo == null) {
            pojo = lookupService();
        }
        return pojo;
    }

    public void setPojo(Object pojo) {
        this.pojo = pojo;
    }

    public String getPojoName() {
        return pojoName;
    }

    protected Object lookupService() {
        return getPojoComponent().getService(getPojoName());
    }
}
