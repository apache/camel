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
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.jsmpp.bean.AlertNotification;
import org.jsmpp.bean.Command;
import org.jsmpp.bean.DataSm;
import org.jsmpp.bean.DeliverSm;
import org.jsmpp.bean.DeliveryReceipt;
import org.jsmpp.bean.OptionalParameter;
import org.jsmpp.bean.OptionalParameter.COctetString;
import org.jsmpp.bean.OptionalParameter.Null;
import org.jsmpp.bean.OptionalParameter.OctetString;
import org.jsmpp.bean.OptionalParameter.Tag;
import org.jsmpp.session.SMPPSession;
import org.jsmpp.util.DefaultDecomposer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Strategy used to convert between a Camel {@link Exchange} and {@link SmppMessage} to and from a SMPP
 * {@link Command}
 */
public class SmppBinding {

    private static final Logger LOG = LoggerFactory.getLogger(SmppBinding.class);

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

        return commandType.createCommand(session, configuration);
    }

    /**
     * Create a new SmppMessage from the inbound alert notification
     */
    public SmppMessage createSmppMessage(CamelContext camelContext, AlertNotification alertNotification) {
        SmppMessage smppMessage = new SmppMessage(camelContext, alertNotification, configuration);

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
    public SmppMessage createSmppMessage(CamelContext camelContext, DeliverSm deliverSm) throws Exception {
        SmppMessage smppMessage = new SmppMessage(camelContext, deliverSm, configuration);

        byte[] body = SmppUtils.getMessageBody(deliverSm);
        String decodedBody = SmppUtils.decodeBody(body, deliverSm.getDataCoding(), configuration.getEncoding());

        if (deliverSm.isSmscDeliveryReceipt()) {
            smppMessage.setHeader(SmppConstants.MESSAGE_TYPE, SmppMessageType.DeliveryReceipt.toString());

            DeliveryReceipt smscDeliveryReceipt = null;
            if (decodedBody != null) {
                smscDeliveryReceipt = DefaultDecomposer.getInstance().deliveryReceipt(decodedBody);
            } else if (body != null) {
                // fallback approach
                smscDeliveryReceipt = DefaultDecomposer.getInstance().deliveryReceipt(body);
            }

            if (smscDeliveryReceipt != null) {
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
            }
        } else {
            smppMessage.setHeader(SmppConstants.MESSAGE_TYPE, SmppMessageType.DeliverSm.toString());

            if (body != null) {
                smppMessage.setBody((decodedBody != null) ? decodedBody : body);
            }

            smppMessage.setHeader(SmppConstants.SEQUENCE_NUMBER, deliverSm.getSequenceNumber());
            smppMessage.setHeader(SmppConstants.COMMAND_ID, deliverSm.getCommandId());
            smppMessage.setHeader(SmppConstants.SOURCE_ADDR, deliverSm.getSourceAddr());
            smppMessage.setHeader(SmppConstants.SOURCE_ADDR_NPI, deliverSm.getSourceAddrNpi());
            smppMessage.setHeader(SmppConstants.SOURCE_ADDR_TON, deliverSm.getSourceAddrTon());
            smppMessage.setHeader(SmppConstants.DATA_CODING, deliverSm.getDataCoding());
            smppMessage.setHeader(SmppConstants.DEST_ADDR, deliverSm.getDestAddress());
            smppMessage.setHeader(SmppConstants.DEST_ADDR_NPI, deliverSm.getDestAddrNpi());
            smppMessage.setHeader(SmppConstants.DEST_ADDR_TON, deliverSm.getDestAddrTon());
            smppMessage.setHeader(SmppConstants.SCHEDULE_DELIVERY_TIME, deliverSm.getScheduleDeliveryTime());
            smppMessage.setHeader(SmppConstants.VALIDITY_PERIOD, deliverSm.getValidityPeriod());
            smppMessage.setHeader(SmppConstants.SERVICE_TYPE, deliverSm.getServiceType());
        }

        if (deliverSm.getOptionalParameters() != null && deliverSm.getOptionalParameters().length > 0) {
            // the deprecated way
            Map<String, Object> optionalParameters = createOptionalParameterByName(deliverSm);
            smppMessage.setHeader(SmppConstants.OPTIONAL_PARAMETERS, optionalParameters);

            // the new way
            Map<Short, Object> optionalParameter = createOptionalParameterByCode(deliverSm);
            smppMessage.setHeader(SmppConstants.OPTIONAL_PARAMETER, optionalParameter);
        }
        return smppMessage;
    }

    private Map<String, Object> createOptionalParameterByName(DeliverSm deliverSm) {
        Map<String, Object> optParams = new HashMap<>();
        for (OptionalParameter optPara : deliverSm.getOptionalParameters()) {
            try {
                Tag valueOfTag = OptionalParameter.Tag.valueOf(optPara.tag);
                if (valueOfTag != null) {
                    if (optPara instanceof COctetString) {
                        optParams.put(valueOfTag.toString(), ((COctetString) optPara).getValueAsString());
                    } else if (optPara instanceof OctetString) {
                        optParams.put(valueOfTag.toString(), ((OctetString) optPara).getValueAsString());
                    } else if (optPara instanceof OptionalParameter.Byte) {
                        optParams.put(valueOfTag.toString(),
                                ((OptionalParameter.Byte) optPara).getValue());
                    } else if (optPara instanceof OptionalParameter.Short) {
                        optParams.put(valueOfTag.toString(),
                                ((OptionalParameter.Short) optPara).getValue());
                    } else if (optPara instanceof OptionalParameter.Int) {
                        optParams.put(valueOfTag.toString(),
                                ((OptionalParameter.Int) optPara).getValue());
                    } else if (optPara instanceof Null) {
                        optParams.put(valueOfTag.toString(), null);
                    }
                } else {
                    LOG.debug("Skipping optional parameter with tag {} because it was not recognized", optPara.tag);
                }
            } catch (IllegalArgumentException e) {
                LOG.debug("Skipping optional parameter with tag {} due to {}", optPara.tag, e.getMessage());
            }
        }
        return optParams;
    }

    private Map<Short, Object> createOptionalParameterByCode(DeliverSm deliverSm) {
        Map<Short, Object> optParams = new HashMap<>();
        for (OptionalParameter optPara : deliverSm.getOptionalParameters()) {
            if (optPara instanceof COctetString) {
                optParams.put(optPara.tag, ((COctetString) optPara).getValueAsString());
            } else if (optPara instanceof OctetString) {
                optParams.put(optPara.tag, ((OctetString) optPara).getValue());
            } else if (optPara instanceof OptionalParameter.Byte) {
                optParams.put(optPara.tag,
                        ((OptionalParameter.Byte) optPara).getValue());
            } else if (optPara instanceof OptionalParameter.Short) {
                optParams.put(optPara.tag,
                        ((OptionalParameter.Short) optPara).getValue());
            } else if (optPara instanceof OptionalParameter.Int) {
                optParams.put(optPara.tag,
                        ((OptionalParameter.Int) optPara).getValue());
            } else if (optPara instanceof Null) {
                optParams.put(optPara.tag, null);
            }
        }
        return optParams;
    }

    public SmppMessage createSmppMessage(CamelContext camelContext, DataSm dataSm, String smppMessageId) {
        SmppMessage smppMessage = new SmppMessage(camelContext, dataSm, configuration);

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
