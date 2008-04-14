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
package org.apache.camel.component.ibatis;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.impl.PollingConsumerSupport;

/**
 * @version $Revision$
 */
public class IBatisPollingConsumer extends PollingConsumerSupport {
    private final IBatisEndpoint endpoint;

    public IBatisPollingConsumer(IBatisEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    public Exchange receive(long timeout) {
        return receiveNoWait();
    }

    public Exchange receive() {
        return receiveNoWait();
    }

    public Exchange receiveNoWait() {
        try {
            Exchange exchange = endpoint.createExchange();
            Message in = exchange.getIn();
            endpoint.query(in);
            return exchange;
        } catch (Exception e) {
            throw new RuntimeCamelException("Failed to poll: " + endpoint + ". Reason: " + e, e);
        }
    }

    protected void doStart() throws Exception {
    }

    protected void doStop() throws Exception {
    }
}
