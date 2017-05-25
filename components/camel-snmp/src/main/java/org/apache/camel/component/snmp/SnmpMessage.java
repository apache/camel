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
package org.apache.camel.component.snmp;

import org.apache.camel.CamelContext;
import org.apache.camel.impl.DefaultMessage;
import org.snmp4j.CommandResponderEvent;
import org.snmp4j.PDU;
import org.snmp4j.smi.OctetString;

public class SnmpMessage extends DefaultMessage {
    private PDU pdu;

    public SnmpMessage(CamelContext camelContext, PDU pdu) {
        super(camelContext);
        this.pdu = pdu;
    }

    public SnmpMessage(CamelContext camelContext, PDU pdu, CommandResponderEvent event) {
        super(camelContext);
        this.pdu = pdu;
        this.setHeader("securityName", new OctetString(event.getSecurityName()));
        this.setHeader("peerAddress", event.getPeerAddress());
    }

    @Override
    public String toString() {
        if (pdu != null) {
            return "SnmpMessage: " + SnmpConverters.toString(pdu);
        } else {
            return "SnmpMessage: " + getBody();
        }
    }

    /**
     * Returns the underlying SNMP message
     */
    public PDU getSnmpMessage() {
        return this.pdu;
    }

    @Override
    public SnmpMessage newInstance() {
        return new SnmpMessage(getCamelContext(), this.pdu);
    }

    @Override
    protected Object createBody() {
        if (this.pdu != null) {
            return SnmpConverters.toString(this.pdu);
        }
        return null;
    }
}
