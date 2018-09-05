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
package org.apache.camel.component.ignite.idgen;

import org.apache.camel.AsyncCallback;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.component.ignite.IgniteConstants;
import org.apache.camel.impl.DefaultAsyncProducer;
import org.apache.camel.util.MessageHelper;
import org.apache.ignite.IgniteAtomicSequence;

/**
 * Ignite ID Generator producer.
 */
public class IgniteIdGenProducer extends DefaultAsyncProducer {

    private IgniteIdGenEndpoint endpoint;
    private IgniteAtomicSequence atomicSeq;

    public IgniteIdGenProducer(IgniteIdGenEndpoint endpoint, IgniteAtomicSequence atomicSeq) {
        super(endpoint);
        this.endpoint = endpoint;
        this.atomicSeq = atomicSeq;
    }

    @Override
    public boolean process(Exchange exchange, AsyncCallback callback) {
        Message in = exchange.getIn();
        Message out = exchange.getOut();
        MessageHelper.copyHeaders(in, out, true);

        Long id = in.getBody(Long.class);

        switch (idGenOperationFor(exchange)) {

        case ADD_AND_GET:
            out.setBody(atomicSeq.addAndGet(id));
            break;

        case GET:
            out.setBody(atomicSeq.get());
            break;

        case GET_AND_ADD:
            out.setBody(atomicSeq.getAndAdd(id));
            break;

        case GET_AND_INCREMENT:
            out.setBody(atomicSeq.getAndIncrement());
            break;

        case INCREMENT_AND_GET:
            out.setBody(atomicSeq.incrementAndGet());
            break;
            
        default:
            exchange.setException(new UnsupportedOperationException("Operation not supported by Ignite ID Generator producer."));
            return true;
        }

        return true;
    }

    private IgniteIdGenOperation idGenOperationFor(Exchange exchange) {
        return exchange.getIn().getHeader(IgniteConstants.IGNITE_IDGEN_OPERATION, endpoint.getOperation(), IgniteIdGenOperation.class);
    }

}
