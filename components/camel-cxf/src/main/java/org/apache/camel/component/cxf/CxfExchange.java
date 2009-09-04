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

import org.apache.camel.CamelContext;
import org.apache.camel.ExchangePattern;
import org.apache.camel.impl.DefaultExchange;
import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.Message;
import org.apache.cxf.transport.Conduit;
import org.apache.cxf.transport.Destination;

/**
 * An {@link Exchange} for working with Apache CXF which exposes the underlying
 * CXF messages via {@link #getInMessage()} and {@link #getOutMessage()} along with the
 * {@link #getExchange()}
 *
 * @version $Revision$
 */
public class CxfExchange extends DefaultExchange {
    private Exchange exchange;

    public CxfExchange(CamelContext context, ExchangePattern pattern, Exchange exchange) {
        super(context, pattern);
        this.exchange = exchange;
        // TO avoid the NPE here
        if (exchange != null) {
            if (exchange.getOutMessage() != null) {
                setOut(new CxfMessage(exchange.getOutMessage()));
            }
            if (exchange.getInMessage() != null) {
                setIn(new CxfMessage(exchange.getInMessage()));
            }
            if (exchange.getInFaultMessage() != null) {
                setFault(new CxfMessage(exchange.getInFaultMessage()));
            }
        }
    }

    public CxfExchange(CamelContext context, ExchangePattern pattern) {
        super(context, pattern);
    }
    
    public CxfExchange(CxfExchange exchange) {
        super(exchange);
        this.exchange = exchange.getExchange();
        
        if (DataFormat.MESSAGE == exchange.getProperty(CxfConstants.DATA_FORMAT_PROPERTY, DataFormat.class)) {
            // message body is an input stream so it is not copied to new exchange
            return;
        }
        
        // Just make sure the wrapped CXF message be copied
        
        CxfMessage message = (CxfMessage)exchange.getIn();
        if (message != null) {
            this.setIn(new CxfMessage(message.getMessage()));
        }
        
        message = (CxfMessage) exchange.getOut(false);
        if (message != null) {
            this.setOut(new CxfMessage(message.getMessage()));
        }
        
        message = (CxfMessage) exchange.getFault(false);
        if (message != null) {
            this.setFault(new CxfMessage(message.getMessage()));
        }
        
    }

    public CxfExchange(CamelContext context, ExchangePattern pattern, Message inMessage) {
        this(context, pattern);
        this.exchange = inMessage.getExchange();

        setIn(new CxfMessage(inMessage));
        if (exchange != null) {
            if (exchange.getOutMessage() != null) {
                setOut(new CxfMessage(exchange.getOutMessage()));
            }
            if (exchange.getInFaultMessage() != null) {
                setFault(new CxfMessage(exchange.getInFaultMessage()));
            }
        }
    }

    @Override
    public org.apache.camel.Exchange newInstance() {
        return new CxfExchange(this);
    }

    @Override
    protected org.apache.camel.Message createFaultMessage() {
        return new CxfMessage();
    }


    public void setExchange(Exchange exchange) {
        this.exchange = exchange;
    }

    // Expose CXF APIs directly on the exchange
    //-------------------------------------------------------------------------

    /**
     * Returns the underlying CXF message exchange for an inbound exchange
     * or null for outbound messages
     *
     * @return the inbound message exchange
     */
    public Exchange getExchange() {
        return exchange;
    }

    public Message getInMessage() {
        if (getIn() instanceof CxfMessage) {
            return ((CxfMessage)getIn()).getMessage();
        } else {
            return null;
        }
    }

    public Message getOutMessage() {
        if (getOut() instanceof CxfMessage) {
            return ((CxfMessage)getOut()).getMessage();
        } else {
            return null;
        }
    }

    public Message getOutFaultMessage() {
        return getExchange().getOutFaultMessage();
    }

    public Message getInFaultMessage() {
        return getExchange().getInFaultMessage();
    }

    public Destination getDestination() {
        return getExchange().getDestination();
    }

    public Conduit getConduit(Message message) {
        return getExchange().getConduit(message);
    }

    @Override
    protected org.apache.camel.Message createInMessage() {
        return new CxfMessage();
    }

    @Override
    protected org.apache.camel.Message createOutMessage() {
        return new CxfMessage();
    }
}
