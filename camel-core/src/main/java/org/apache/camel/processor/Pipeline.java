/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.processor;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Processor;

import java.util.Collection;

/**
 * Creates a Pipeline pattern where the output of the previous step is sent as input to the next step when working
 * with request/response message exchanges.
 *  
 * @version $Revision$
 */
public class Pipeline<E extends Exchange> implements Processor<E> {
    private Collection<Endpoint<E>> endpoints;

    public Pipeline(Collection<Endpoint<E>> endpoints) {
        this.endpoints = endpoints;
    }

    public void onExchange(E exchange) {
        E nextExchange = exchange;
        boolean first = true;
        for (Endpoint<E> endpoint : endpoints) {
            if (first) {
                first = false;
            }
            else {
                nextExchange = createNextExchange(endpoint, nextExchange);
            }
            endpoint.onExchange(nextExchange);
        }
    }

    /**
     * Strategy method to create the next exchange from the
     *
     * @param endpoint the endpoint the exchange will be sent to
     * @param previousExchange the previous exchange
     * @return a new exchange
     */
    protected E createNextExchange(Endpoint<E> endpoint, E previousExchange) {
        E answer = endpoint.createExchange(previousExchange);

        // now lets set the input of the next exchange to the output of the previous message if it is not null
        Object output = previousExchange.getOut().getBody();
        if (output != null) {
            answer.getIn().setBody(output);
        }
        return answer;
    }

    /**
     * Strategy method to copy the exchange before sending to another endpoint. Derived classes such as the
     * {@link Pipeline} will not clone the exchange
     *
     * @param exchange
     * @return the current exchange if no copying is required such as for a pipeline otherwise a new copy of the exchange is returned.
     */
    protected E copyExchangeStrategy(E exchange) {
        return (E) exchange.copy();
    }

    @Override
    public String toString() {
        return "Pipeline" + endpoints;
    }
}
