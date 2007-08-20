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
package org.apache.camel.component.atom;

import org.apache.abdera.model.Document;
import org.apache.abdera.model.Feed;
import org.apache.abdera.util.iri.IRISyntaxException;
import org.apache.camel.Exchange;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.impl.PollingConsumerSupport;

import java.io.IOException;

/**
 * @version $Revision: 1.1 $
 */
public class AtomPollingConsumer extends PollingConsumerSupport {
    private final AtomEndpoint endpoint;

    public AtomPollingConsumer(AtomEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    protected void doStart() throws Exception {
    }

    protected void doStop() throws Exception {
    }

    public Exchange receiveNoWait() {
        try {
            Document<Feed> document = endpoint.parseDocument();
            Exchange exchange = endpoint.createExchange();
            exchange.getIn().setBody(document);
            return exchange;
        }
        catch (IRISyntaxException e) {
            throw new RuntimeCamelException(e);
        }
        catch (IOException e) {
            throw new RuntimeCamelException(e);
        }
    }

    public Exchange receive() {
        return receiveNoWait();
    }

    public Exchange receive(long timeout) {
        return receiveNoWait();
    }
}
