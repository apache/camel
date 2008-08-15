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
package org.apache.camel.component.jt400;

import java.io.IOException;

import com.ibm.as400.access.AS400;
import com.ibm.as400.access.AS400SecurityException;
import com.ibm.as400.access.DataQueue;
import com.ibm.as400.access.DataQueueEntry;
import com.ibm.as400.access.ErrorCompletingRequestException;
import com.ibm.as400.access.IllegalObjectTypeException;
import com.ibm.as400.access.ObjectDoesNotExistException;

import org.apache.camel.Exchange;
import org.apache.camel.PollingConsumer;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.jt400.Jt400DataQueueEndpoint.Format;
import org.apache.camel.impl.DefaultExchange;
import org.apache.camel.impl.PollingConsumerSupport;


/**
 * {@link PollingConsumer} that polls a data queue for data
 */
public class Jt400DataQueueConsumer extends PollingConsumerSupport<Exchange> {

    private final Jt400DataQueueEndpoint endpoint;

    /**
     * Creates a new consumer instance
     */
    protected Jt400DataQueueConsumer(Jt400DataQueueEndpoint endpoint) {
        super(endpoint);
        this.endpoint = endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        if (!endpoint.getSystem().isConnected()) {
            endpoint.getSystem().connectService(AS400.DATAQUEUE);
        }
    }

    @Override
    protected void doStop() throws Exception {
        if (endpoint.getSystem().isConnected()) {
            endpoint.getSystem().disconnectAllServices();
        }
    }

    /**
     * {@link Jt400DataQueueConsumer#receive(long)}
     */
    public Exchange receive() {
        // -1 to indicate a blocking read from data queue
        return receive(-1);
    }

    /**
     * {@link Jt400DataQueueConsumer#receive(long)}
     */
    public Exchange receiveNoWait() {
        return receive(0);
    }

    /**
     * Receives an entry from a data queue and returns an {@link Exchange} to
     * send this data If the endpoint's format is set to {@link Format#binary},
     * the data queue entry's data will be received/sent as a
     * <code>byte[]</code>. If the endpoint's format is set to
     * {@link Format#text}, the data queue entry's data will be received/sent as
     * a <code>String</code>.
     *
     * @param timeout time to wait when reading from data queue. A value of -1
     *            indicates a blocking read.
     */
    public Exchange receive(long timeout) {
        DataQueue queue = endpoint.getDataQueue();
        try {
            DataQueueEntry entry;
            if (timeout >= 0) {
                entry = queue.read((int)timeout);
            } else {
                entry = queue.read();
            }
            Exchange exchange = new DefaultExchange(endpoint.getCamelContext());
            if (entry != null) {
                if (endpoint.getFormat() == Format.binary) {
                    exchange.getIn().setBody(entry.getData());
                } else {
                    exchange.getIn().setBody(entry.getString());
                }
                return exchange;
            }
        } catch (AS400SecurityException e) {
            throw new RuntimeCamelException("Unable to read from data queue: " + e.getMessage(), e);
        } catch (ErrorCompletingRequestException e) {
            throw new RuntimeCamelException("Unable to read from data queue: " + e.getMessage(), e);
        } catch (IOException e) {
            throw new RuntimeCamelException("Unable to read from data queue: " + e.getMessage(), e);
        } catch (IllegalObjectTypeException e) {
            throw new RuntimeCamelException("Unable to read from data queue: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            throw new RuntimeCamelException("Unable to read from data queue: " + e.getMessage(), e);
        } catch (ObjectDoesNotExistException e) {
            throw new RuntimeCamelException("Unable to read from data queue: " + e.getMessage(), e);
        }
        return null;
    }
}
