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
package org.apache.camel.component.seda;

import java.util.List;

import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.impl.PollingConsumerSupport;

/**
 * @deprecated not used. Will be removed in Camel 2.0.
 * @version $Revision$
 */
@Deprecated
public class ListPollingConsumer extends PollingConsumerSupport {
    private final List<Exchange> exchanges;

    public ListPollingConsumer(Endpoint endpoint, List<Exchange> exchanges) {
        super(endpoint);
        this.exchanges = exchanges;
    }

    public Exchange receive() {
        return receiveNoWait();
    }

    public Exchange receiveNoWait() {
        if (exchanges.isEmpty()) {
            return null;
        } else {
            return exchanges.remove(0);
        }
    }

    public Exchange receive(long timeout) {
        return receiveNoWait();
    }

    protected void doStart() throws Exception {
    }

    protected void doStop() throws Exception {
    }
}
