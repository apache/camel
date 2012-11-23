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
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import org.apache.camel.Exchange;
import org.jsmpp.bean.AlertNotification;
import org.jsmpp.bean.Alphabet;
import org.jsmpp.bean.Command;
import org.jsmpp.bean.DataSm;
import org.jsmpp.bean.DeliverSm;
import org.jsmpp.bean.DeliveryReceipt;
import org.jsmpp.bean.OptionalParameter;
import org.jsmpp.bean.OptionalParameter.OctetString;
import org.jsmpp.session.SMPPSession;

/**
 * A Strategy used to convert between a Camel {@link Exchange} and
 * {@link SmppMessage} to and from a SMPP {@link Command}
 * 
 * @version 
 */
public class SmppBinding {

    private SmppConfiguration configuration;

    public SmppBinding() {
        this.configuration = new SmppConfiguration();
    }

    public SmppBinding(SmppConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * Create the SmppCommand object from the inbound exchange
     *
     * @throws UnsupportedEncodingException if the encoding is not supported
     */
    public SmppCommand createSmppCommand(SMPPSession session, Exchange exchange) {
        SmppCommandType commandType = SmppCommandType.fromExchange(exchange);
        SmppCommand command = commandType.createCommand(session, configuration);
        
        return command;
    }

    /**
     * Create a new SmppMessage from the inbound alert notification
     */
    public SmppMessage createSmppMessage(AlertNotification alertNotification) {
        SmppMessage smppMessage = new SmppMessage(alertNotification, configuration);

        smppMessage.setHeader(SmppConstants.MESSAGE_TYPE, SmppMessageType.AlertNotification.toString());
        smppMessage.setHeader(SmppConstants.SEQUENCE_NUMBER, alertNotification.getSequenceNumber());
        smppMessage.setHeader(SmppConstants.COMMAND_ID, alertNotification.getCommandId());
        smppMessage.setHeader(SmppConstants.COMMAND_STATUS, alertNotification.getCommandStatus());
        smppMessage.setHeader(SmppConstants.SOURCE_ADDR, alertNotification.getSourceAddr());
        smppMessage.setHeader(SmppConstants.SOURCE_ADDR_NPI, alertNotification.getSourceAddrNpi());
        smppMessage.setHeader(SmppConstants.SOURCE_ADDR_TON, alertNotification.getSourceAddrTon());
        smppMessage.setHeader(SmppConstants.ESME_ADDR, alertNotification.getEsmeAddr());
        smppMessage.setHeader(SmppConstants.ESME_ADDR_NPI, alertNotification.getEsmeAddrNpi());
        smppMessage.setHeader(SmppConstants.ESME_ADDR_TON, alertNotification.getEsmeAddrTon());

        return smppMessage;
    }

    /**
     * Create a new SmppMessage from the inbound deliver sm or deliver receipt
     */
    public SmppMessage createSmppMessage(DeliverSm deliverSm) throws Exception {
        SmppMessage smppMessage = new SmppMessage(deliverSm, configuration);

        if (deliverSm.isSmscDeliveryReceipt()) {
            smppMessage.setHeader(SmppConstants.MESSAGE_TYPE, SmppMessageType.DeliveryReceipt.toString());
            DeliveryReceipt smscDeliveryReceipt = deliverSm.getShortMessageAsDeliveryReceipt();
            smppMessage.setBody(smscDeliveryReceipt.getText());

            smppMessage.setHeader(SmppConstants.ID, smscDeliveryReceipt.getId());
            smppMessage.setHeader(SmppConstants.DELIVERED, smscDeliveryReceipt.getDelivered());
            smppMessage.setHeader(SmppConstants.DONE_DATE, smscDeliveryReceipt.getDoneDate());
            if (!"000".equals(smscDeliveryReceipt.getError())) {
                smppMessage.setHeader(SmppConstants.ERROR, smscDeliveryReceipt.getError());
            }
            smppMessage.setHeader(SmppConstants.SUBMIT_DATE, smscDeliveryReceipt.getSubmitDate());
            smppMessage.setHeader(SmppConstants.SUBMITTED, smscDeliveryReceipt.getSubmitted());
            smppMessage.setHeader(SmppConstants.FINAL_STATUS, smscDeliveryReceipt.getFinalStatus());
        } else {
            smppMessage.setHeader(SmppConstants.MESSAGE_TYPE, SmppMessageType.DeliverSm.toString());

            if (deliverSm.getShortMessage() != null) {
                if (SmppUtils.parseAlphabetFromDataCoding(deliverSm.getDataCoding()) == Alphabet.ALPHA_8_BIT) {
                    smppMessage.setBody(deliverSm.getShortMessage());
                } else {
                    smppMessage.setBody(String.valueOf(new String(deliverSm.getShortMessage(),
                                                                  configuration.getEncoding())));
                }
            } else if (deliverSm.getOptionalParametes() != null && deliverSm.getOptionalParametes().length > 0) {
                List<OptionalParameter> oplist = Arrays.asList(deliverSm.getOptionalParametes());

                for (OptionalParameter optPara : oplist) {
                    if (OptionalParameter.Tag.MESSAGE_PAYLOAD.code() == optPara.tag && OctetString.class.isInstance(optPara)) {
                        smppMessage.setBody(((OctetString) optPara).getValueAsString());
                        break;
                    }
                }
            }

            smppMessage.setHeader(SmppConstants.SEQUENCE_NUMBER, deliverSm.getSequenceNumber());
            smppMessage.setHeader(SmppConstants.COMMAND_ID, deliverSm.getCommandId());
            smppMessage.setHeader(SmppConstants.SOURCE_ADDR, deliverSm.getSourceAddr());
            smppMessage.setHeader(SmppConstants.SOURCE_ADDR_NPI, deliverSm.getSourceAddrNpi());
            smppMessage.setHeader(SmppConstants.SOURCE_ADDR_TON, deliverSm.getSourceAddrTon());
            smppMessage.setHeader(SmppConstants.DEST_ADDR, deliverSm.getDestAddress());
            smppMessage.setHeader(SmppConstants.DEST_ADDR_NPI, deliverSm.getDestAddrNpi());
            smppMessage.setHeader(SmppConstants.DEST_ADDR_TON, deliverSm.getDestAddrTon());
            smppMessage.setHeader(SmppConstants.SCHEDULE_DELIVERY_TIME, deliverSm.getScheduleDeliveryTime());
            smppMessage.setHeader(SmppConstants.VALIDITY_PERIOD, deliverSm.getValidityPeriod());
            smppMessage.setHeader(SmppConstants.SERVICE_TYPE, deliverSm.getServiceType());
        }

        return smppMessage;
    }
    
    public SmppMessage createSmppMessage(DataSm dataSm, String smppMessageId) {
        SmppMessage smppMessage = new SmppMessage(dataSm, configuration);

        smppMessage.setHeader(SmppConstants.MESSAGE_TYPE, SmppMessageType.DataSm.toString());
        smppMessage.setHeader(SmppConstants.ID, smppMessageId);
        smppMessage.setHeader(SmppConstants.SEQUENCE_NUMBER, dataSm.getSequenceNumber());
        smppMessage.setHeader(SmppConstants.COMMAND_ID, dataSm.getCommandId());
        smppMessage.setHeader(SmppConstants.COMMAND_STATUS, dataSm.getCommandStatus());
        smppMessage.setHeader(SmppConstants.SOURCE_ADDR, dataSm.getSourceAddr());
        smppMessage.setHeader(SmppConstants.SOURCE_ADDR_NPI, dataSm.getSourceAddrNpi());
        smppMessage.setHeader(SmppConstants.SOURCE_ADDR_TON, dataSm.getSourceAddrTon());
        smppMessage.setHeader(SmppConstants.DEST_ADDR, dataSm.getDestAddress());
        smppMessage.setHeader(SmppConstants.DEST_ADDR_NPI, dataSm.getDestAddrNpi());
        smppMessage.setHeader(SmppConstants.DEST_ADDR_TON, dataSm.getDestAddrTon());
        smppMessage.setHeader(SmppConstants.SERVICE_TYPE, dataSm.getServiceType());
        smppMessage.setHeader(SmppConstants.REGISTERED_DELIVERY, dataSm.getRegisteredDelivery());
        smppMessage.setHeader(SmppConstants.DATA_CODING, dataSm.getDataCoding());

        return smppMessage;
    }
    
    /**
     * Returns the current date. Externalized for better test support.
     * 
     * @return the current date
     */
    Date getCurrentDate() {
        return new Date();
    }

    /**
     * Returns the smpp configuration
     * 
     * @return the configuration
     */
    public SmppConfiguration getConfiguration() {
        return configuration;
    }

    /**
     * Set the smpp configuration.
     * 
     * @param configuration smppConfiguration
     */
    public void setConfiguration(SmppConfiguration configuration) {
        this.configuration = configuration;
    }
}