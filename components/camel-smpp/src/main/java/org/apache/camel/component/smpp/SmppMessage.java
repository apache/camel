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
package org.apache.camel.component.smpp;

import java.io.UnsupportedEncodingException;

import org.apache.camel.impl.DefaultMessage;
import org.jsmpp.bean.AlertNotification;
import org.jsmpp.bean.Command;
import org.jsmpp.bean.DataSm;
import org.jsmpp.bean.DeliverSm;
import org.jsmpp.bean.MessageRequest;

/**
 * Represents a {@link org.apache.camel.Message} for working with SMPP
 * 
 * @author muellerc
 * @version $Revision$
 */
public class SmppMessage extends DefaultMessage {

    private Command command;
    private SmppConfiguration configuration;
    
    public SmppMessage(SmppConfiguration configuration) {
        this.configuration = configuration;
    }

    public SmppMessage(AlertNotification command, SmppConfiguration configuration) {
        this.command = command;
        this.configuration = configuration;
    }
    
    public SmppMessage(DeliverSm command, SmppConfiguration configuration) {
        this.command = command;
        this.configuration = configuration;
    }

    public SmppMessage(DataSm dataSm, SmppConfiguration configuration) {
        this.command = dataSm;
        this.configuration = configuration;
    }

    @Override
    public SmppMessage newInstance() {
        return new SmppMessage(this.configuration);
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
            byte[] shortMessage = ((MessageRequest) command).getShortMessage();
            try {
                return new String(shortMessage, configuration.getEncoding());
            } catch (UnsupportedEncodingException e) {
                return new String(shortMessage);
            }
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