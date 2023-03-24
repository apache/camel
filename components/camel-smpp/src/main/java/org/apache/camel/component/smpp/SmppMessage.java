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
package org.apache.camel.component.smpp;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;

import org.apache.camel.CamelContext;
import org.apache.camel.support.DefaultMessage;
import org.apache.camel.support.ExchangeHelper;
import org.apache.camel.util.ObjectHelper;
import org.jsmpp.bean.AlertNotification;
import org.jsmpp.bean.Alphabet;
import org.jsmpp.bean.Command;
import org.jsmpp.bean.DataSm;
import org.jsmpp.bean.DeliverSm;
import org.jsmpp.bean.MessageRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represents a {@link org.apache.camel.Message} for working with SMPP
 */
public class SmppMessage extends DefaultMessage {
    private static final Logger LOG = LoggerFactory.getLogger(SmppMessage.class);
    private Command command;
    private SmppConfiguration configuration;

    public SmppMessage(CamelContext camelContext, Command command, SmppConfiguration configuration) {
        super(camelContext);
        this.command = command;
        this.configuration = configuration;
    }

    @Override
    public SmppMessage newInstance() {
        return new SmppMessage(getCamelContext(), null, this.configuration);
    }

    public boolean isAlertNotification() {
        return command instanceof AlertNotification;
    }

    public boolean isDataSm() {
        return command instanceof DataSm;
    }

    public boolean isDeliverSm() {
        return command instanceof DeliverSm && !((DeliverSm) command).isSmscDeliveryReceipt();
    }

    public boolean isDeliveryReceipt() {
        return command instanceof DeliverSm && ((DeliverSm) command).isSmscDeliveryReceipt();
    }

    @Override
    protected Object createBody() {
        if (command instanceof MessageRequest) {
            MessageRequest msgRequest = (MessageRequest) command;
            byte[] shortMessage = msgRequest.getShortMessage();
            if (shortMessage == null || shortMessage.length == 0) {
                return null;
            }
            Alphabet alphabet = Alphabet.parseDataCoding(msgRequest.getDataCoding());
            if (SmppUtils.is8Bit(alphabet)) {
                return shortMessage;
            }

            String encoding = ExchangeHelper.getCharsetName(getExchange(), false);
            if (ObjectHelper.isEmpty(encoding) || !Charset.isSupported(encoding)) {
                encoding = configuration.getEncoding();
            }
            try {
                return new String(shortMessage, encoding);
            } catch (UnsupportedEncodingException e) {
                LOG.info("Unsupported encoding \"{}\". Using system default encoding.", encoding);
            }
            return new String(shortMessage);
        }

        return null;
    }

    @Override
    public String toString() {
        if (command != null) {
            return "SmppMessage: " + command;
        } else {
            return "SmppMessage: " + getBody();
        }
    }

    /**
     * Returns the underlying jSMPP command
     *
     * @return command
     */
    public Command getCommand() {
        return command;
    }
}
