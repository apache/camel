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
import org.apache.camel.Message;
import org.jsmpp.bean.AlertNotification;
import org.jsmpp.bean.Command;
import org.jsmpp.bean.DataSm;
import org.jsmpp.bean.DeliverSm;
import org.jsmpp.bean.DeliveryReceipt;
import org.jsmpp.bean.OptionalParameter;
import org.jsmpp.bean.OptionalParameter.OctetString;
import org.jsmpp.bean.SubmitSm;
import org.jsmpp.util.AbsoluteTimeFormatter;
import org.jsmpp.util.TimeFormatter;

/**
 * A Strategy used to convert between a Camel {@link Exchange} and
 * {@link SmppMessage} to and from a SMPP {@link Command}
 * 
 * @version 
 */
public class SmppBinding {

    public static final String SEQUENCE_NUMBER = "CamelSmppSequenceNumber";
    public static final String SUBMITTED = "CamelSmppSubmitted";
    public static final String SUBMIT_DATE = "CamelSmppSubmitDate";
    public static final String ERROR = "CamelSmppError";
    public static final String DONE_DATE = "CamelSmppDoneDate";
    public static final String DELIVERED = "CamelSmppDelivered";
    public static final String COMMAND_ID = "CamelSmppCommandId";
    public static final String COMMAND_STATUS = "CamelSmppCommandStatus";
    public static final String ID = "CamelSmppId";
    public static final String REPLACE_IF_PRESENT_FLAG = "CamelSmppReplaceIfPresentFlag";
    public static final String VALIDITY_PERIOD = "CamelSmppValidityPeriod";
    public static final String SCHEDULE_DELIVERY_TIME = "CamelSmppScheduleDeliveryTime";
    public static final String PRIORITY_FLAG = "CamelSmppPriorityFlag";
    public static final String PROTOCOL_ID = "CamelSmppProtocolId";
    public static final String REGISTERED_DELIVERY = "CamelSmppRegisteredDelivery";
    public static final String SERVICE_TYPE = "CamelSmppServiceType";
    public static final String SOURCE_ADDR_NPI = "CamelSmppSourceAddrNpi";
    public static final String SOURCE_ADDR_TON = "CamelSmppSourceAddrTon";
    public static final String SOURCE_ADDR = "CamelSmppSourceAddr";
    public static final String DEST_ADDR_NPI = "CamelSmppDestAddrNpi";
    public static final String DEST_ADDR_TON = "CamelSmppDestAddrTon";
    public static final String DEST_ADDR = "CamelSmppDestAddr";
    public static final String ESME_ADDR_NPI = "CamelSmppEsmeAddrNpi";
    public static final String ESME_ADDR_TON = "CamelSmppEsmeAddrTon";
    public static final String ESME_ADDR = "CamelSmppEsmeAddr";
    public static final String FINAL_STATUS = "CamelSmppStatus";
    public static final String DATA_CODING = "CamelSmppDataCoding";
    public static final String MESSAGE_TYPE = "CamelSmppMessageType";

    private static TimeFormatter timeFormatter = new AbsoluteTimeFormatter();

    private SmppConfiguration configuration;

    public SmppBinding() {
        this.configuration = new SmppConfiguration();
    }

    public SmppBinding(SmppConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * Create the SubmitSm object from the inbound exchange
     * 
     * @throws UnsupportedEncodingException if the encoding is not supported
     */
    public SubmitSm createSubmitSm(Exchange exchange) throws UnsupportedEncodingException {
        Message in = exchange.getIn();

        SubmitSm submitSm = new SubmitSm();
        submitSm.setShortMessage(exchange.getIn().getBody(String.class).getBytes(configuration.getEncoding()));

        if (in.getHeaders().containsKey(DEST_ADDR)) {
            submitSm.setDestAddress(in.getHeader(DEST_ADDR, String.class));
        } else {
            submitSm.setDestAddress(configuration.getDestAddr());
        }

        if (in.getHeaders().containsKey(DEST_ADDR_TON)) {
            submitSm.setDestAddrTon(in.getHeader(DEST_ADDR_TON, Byte.class));
        } else {
            submitSm.setDestAddrTon(configuration.getDestAddrTon());
        }

        if (in.getHeaders().containsKey(DEST_ADDR_NPI)) {
            submitSm.setDestAddrNpi(in.getHeader(DEST_ADDR_NPI, Byte.class));
        } else {
            submitSm.setDestAddrNpi(configuration.getDestAddrNpi());
        }

        if (in.getHeaders().containsKey(SOURCE_ADDR)) {
            submitSm.setSourceAddr(in.getHeader(SOURCE_ADDR, String.class));
        } else {
            submitSm.setSourceAddr(configuration.getSourceAddr());
        }

        if (in.getHeaders().containsKey(SOURCE_ADDR_TON)) {
            submitSm.setSourceAddrTon(in.getHeader(SOURCE_ADDR_TON, Byte.class));
        } else {
            submitSm.setSourceAddrTon(configuration.getSourceAddrTon());
        }

        if (in.getHeaders().containsKey(SOURCE_ADDR_NPI)) {
            submitSm.setSourceAddrNpi(in.getHeader(SOURCE_ADDR_NPI, Byte.class));
        } else {
            submitSm.setSourceAddrNpi(configuration.getSourceAddrNpi());
        }

        if (in.getHeaders().containsKey(SERVICE_TYPE)) {
            submitSm.setServiceType(in.getHeader(SERVICE_TYPE, String.class));
        } else {
            submitSm.setServiceType(configuration.getServiceType());
        }

        if (in.getHeaders().containsKey(REGISTERED_DELIVERY)) {
            submitSm.setRegisteredDelivery(in.getHeader(REGISTERED_DELIVERY, Byte.class));
        } else {
            submitSm.setRegisteredDelivery(configuration.getRegisteredDelivery());
        }

        if (in.getHeaders().containsKey(PROTOCOL_ID)) {
            submitSm.setProtocolId(in.getHeader(PROTOCOL_ID, Byte.class));
        } else {
            submitSm.setProtocolId(configuration.getProtocolId());
        }

        if (in.getHeaders().containsKey(PRIORITY_FLAG)) {
            submitSm.setPriorityFlag(in.getHeader(PRIORITY_FLAG, Byte.class));
        } else {
            submitSm.setPriorityFlag(configuration.getPriorityFlag());
        }

        if (in.getHeaders().containsKey(SCHEDULE_DELIVERY_TIME)) {
            submitSm.setScheduleDeliveryTime(timeFormatter.format(in.getHeader(SCHEDULE_DELIVERY_TIME, Date.class)));
        } 

        if (in.getHeaders().containsKey(VALIDITY_PERIOD)) {
            submitSm.setValidityPeriod(timeFormatter.format(in.getHeader(VALIDITY_PERIOD, Date.class)));
        }

        if (in.getHeaders().containsKey(REPLACE_IF_PRESENT_FLAG)) {
            submitSm.setReplaceIfPresent(in.getHeader(REPLACE_IF_PRESENT_FLAG, Byte.class));
        } else {
            submitSm.setReplaceIfPresent(configuration.getReplaceIfPresentFlag());
        }
        
        if (in.getHeaders().containsKey(DATA_CODING)) {
            submitSm.setDataCoding(in.getHeader(DATA_CODING, Byte.class));
        } else {
            submitSm.setDataCoding(configuration.getDataCoding());
        }

        return submitSm;
    }

    /**
     * Create a new SmppMessage from the inbound alert notification
     */
    public SmppMessage createSmppMessage(AlertNotification alertNotification) {
        SmppMessage smppMessage = new SmppMessage(alertNotification, configuration);

        smppMessage.setHeader(MESSAGE_TYPE, SmppMessageType.AlertNotification.toString());
        smppMessage.setHeader(SEQUENCE_NUMBER, alertNotification.getSequenceNumber());
        smppMessage.setHeader(COMMAND_ID, alertNotification.getCommandId());
        smppMessage.setHeader(COMMAND_STATUS, alertNotification.getCommandStatus());
        smppMessage.setHeader(SOURCE_ADDR, alertNotification.getSourceAddr());
        smppMessage.setHeader(SOURCE_ADDR_NPI, alertNotification.getSourceAddrNpi());
        smppMessage.setHeader(SOURCE_ADDR_TON, alertNotification.getSourceAddrTon());
        smppMessage.setHeader(ESME_ADDR, alertNotification.getEsmeAddr());
        smppMessage.setHeader(ESME_ADDR_NPI, alertNotification.getEsmeAddrNpi());
        smppMessage.setHeader(ESME_ADDR_TON, alertNotification.getEsmeAddrTon());

        return smppMessage;
    }

    /**
     * Create a new SmppMessage from the inbound deliver sm or deliver receipt
     */
    public SmppMessage createSmppMessage(DeliverSm deliverSm) throws Exception {
        SmppMessage smppMessage = new SmppMessage(deliverSm, configuration);

        if (deliverSm.isSmscDeliveryReceipt()) {
            smppMessage.setHeader(MESSAGE_TYPE, SmppMessageType.DeliveryReceipt.toString());
            DeliveryReceipt smscDeliveryReceipt = deliverSm.getShortMessageAsDeliveryReceipt();
            smppMessage.setBody(smscDeliveryReceipt.getText());

            smppMessage.setHeader(ID, smscDeliveryReceipt.getId());
            smppMessage.setHeader(DELIVERED, smscDeliveryReceipt.getDelivered());
            smppMessage.setHeader(DONE_DATE, smscDeliveryReceipt.getDoneDate());
            if (!"000".equals(smscDeliveryReceipt.getError())) {
                smppMessage.setHeader(ERROR, smscDeliveryReceipt.getError());
            }
            smppMessage.setHeader(SUBMIT_DATE, smscDeliveryReceipt.getSubmitDate());
            smppMessage.setHeader(SUBMITTED, smscDeliveryReceipt.getSubmitted());
            smppMessage.setHeader(FINAL_STATUS, smscDeliveryReceipt.getFinalStatus());
        } else {
            smppMessage.setHeader(MESSAGE_TYPE, SmppMessageType.DeliverSm.toString());
            if (deliverSm.getShortMessage() != null) {
                smppMessage.setBody(String.valueOf(new String(deliverSm.getShortMessage(),
                        configuration.getEncoding())));
            } else if (deliverSm.getOptionalParametes() != null && deliverSm.getOptionalParametes().length > 0) {
                List<OptionalParameter> oplist = Arrays.asList(deliverSm.getOptionalParametes());

                for (OptionalParameter optPara : oplist) {
                    if (OptionalParameter.Tag.MESSAGE_PAYLOAD.code() == optPara.tag && OctetString.class.isInstance(optPara)) {
                        smppMessage.setBody(((OctetString) optPara).getValueAsString());
                        break;
                    }
                }
            }

            smppMessage.setHeader(SEQUENCE_NUMBER, deliverSm.getSequenceNumber());
            smppMessage.setHeader(COMMAND_ID, deliverSm.getCommandId());
            smppMessage.setHeader(SOURCE_ADDR, deliverSm.getSourceAddr());
            smppMessage.setHeader(DEST_ADDR, deliverSm.getDestAddress());
            smppMessage.setHeader(SCHEDULE_DELIVERY_TIME, deliverSm.getScheduleDeliveryTime());
            smppMessage.setHeader(VALIDITY_PERIOD, deliverSm.getValidityPeriod());
            smppMessage.setHeader(SERVICE_TYPE, deliverSm.getServiceType());
        }

        return smppMessage;
    }
    
    public SmppMessage createSmppMessage(DataSm dataSm, String smppMessageId) {
        SmppMessage smppMessage = new SmppMessage(dataSm, configuration);

        smppMessage.setHeader(MESSAGE_TYPE, SmppMessageType.DataSm.toString());
        smppMessage.setHeader(ID, smppMessageId);
        smppMessage.setHeader(SEQUENCE_NUMBER, dataSm.getSequenceNumber());
        smppMessage.setHeader(COMMAND_ID, dataSm.getCommandId());
        smppMessage.setHeader(COMMAND_STATUS, dataSm.getCommandStatus());
        smppMessage.setHeader(SOURCE_ADDR, dataSm.getSourceAddr());
        smppMessage.setHeader(SOURCE_ADDR_NPI, dataSm.getSourceAddrNpi());
        smppMessage.setHeader(SOURCE_ADDR_TON, dataSm.getSourceAddrTon());
        smppMessage.setHeader(DEST_ADDR, dataSm.getDestAddress());
        smppMessage.setHeader(DEST_ADDR_NPI, dataSm.getDestAddrNpi());
        smppMessage.setHeader(DEST_ADDR_TON, dataSm.getDestAddrTon());
        smppMessage.setHeader(SERVICE_TYPE, dataSm.getServiceType());
        smppMessage.setHeader(REGISTERED_DELIVERY, dataSm.getRegisteredDelivery());
        smppMessage.setHeader(DATA_CODING, dataSm.getDataCoding());

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