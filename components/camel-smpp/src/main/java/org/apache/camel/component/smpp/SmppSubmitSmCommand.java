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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.jsmpp.bean.DataCodings;
import org.jsmpp.bean.ESMClass;
import org.jsmpp.bean.GSMSpecificFeature;
import org.jsmpp.bean.MessageMode;
import org.jsmpp.bean.MessageType;
import org.jsmpp.bean.NumberingPlanIndicator;
import org.jsmpp.bean.OptionalParameter;
import org.jsmpp.bean.RegisteredDelivery;
import org.jsmpp.bean.SubmitSm;
import org.jsmpp.bean.TypeOfNumber;
import org.jsmpp.session.SMPPSession;

public class SmppSubmitSmCommand extends SmppSmCommand {

    public SmppSubmitSmCommand(SMPPSession session, SmppConfiguration config) {
        super(session, config);
    }

    @Override
    public void execute(Exchange exchange) throws SmppException {
        SubmitSm[] submitSms = createSubmitSm(exchange);
        List<String> messageIDs = new ArrayList<String>(submitSms.length);
        
        for (int i = 0; i < submitSms.length; i++) {
            SubmitSm submitSm = submitSms[i];
            String messageID;
            if (log.isDebugEnabled()) {
                log.debug("Sending short message {} for exchange id '{}'...", i, exchange.getExchangeId());
            }

            try {
                messageID = session.submitShortMessage(
                        submitSm.getServiceType(),
                        TypeOfNumber.valueOf(submitSm.getSourceAddrTon()),
                        NumberingPlanIndicator.valueOf(submitSm.getSourceAddrNpi()),
                        submitSm.getSourceAddr(),
                        TypeOfNumber.valueOf(submitSm.getDestAddrTon()),
                        NumberingPlanIndicator.valueOf(submitSm.getDestAddrNpi()),
                        submitSm.getDestAddress(),
                        new ESMClass(submitSm.getEsmClass()),
                        submitSm.getProtocolId(),
                        submitSm.getPriorityFlag(),
                        submitSm.getScheduleDeliveryTime(),
                        submitSm.getValidityPeriod(),
                        new RegisteredDelivery(submitSm.getRegisteredDelivery()),
                        submitSm.getReplaceIfPresent(),
                        DataCodings.newInstance(submitSm.getDataCoding()),
                        (byte) 0,
                        submitSm.getShortMessage(),
                        submitSm.getOptionalParameters());
            } catch (Exception e) {
                throw new SmppException(e);
            }

            messageIDs.add(messageID);
        }

        if (log.isDebugEnabled()) {
            log.debug("Sent short message for exchange id '{}' and received message ids '{}'",
                    exchange.getExchangeId(), messageIDs);
        }

        Message message = getResponseMessage(exchange);
        message.setHeader(SmppConstants.ID, messageIDs);
        message.setHeader(SmppConstants.SENT_MESSAGE_COUNT, messageIDs.size());
    }

    protected SubmitSm[] createSubmitSm(Exchange exchange) throws SmppException {

        SubmitSm template = createSubmitSmTemplate(exchange);
        byte[][] segments = splitBody(exchange.getIn());

        ESMClass esmClass = exchange.getIn().getHeader(SmppConstants.ESM_CLASS, ESMClass.class);
        if (null != esmClass) {
            template.setEsmClass(esmClass.value());
            // multipart message
        } else if (segments.length > 1) {
            template.setEsmClass(new ESMClass(MessageMode.DEFAULT, MessageType.DEFAULT, GSMSpecificFeature.UDHI).value());
        }

        SubmitSm[] submitSms = new SubmitSm[segments.length];
        for (int i = 0; i < segments.length; i++) {
            SubmitSm submitSm = SmppUtils.copySubmitSm(template);
            submitSm.setShortMessage(segments[i]);
            submitSms[i] = submitSm;
        }

        return submitSms;
    }

    @SuppressWarnings({"unchecked"})
    protected SubmitSm createSubmitSmTemplate(Exchange exchange) {
        Message in = exchange.getIn();
        SubmitSm submitSm = new SubmitSm();

        if (in.getHeaders().containsKey(SmppConstants.DATA_CODING)) {
            submitSm.setDataCoding(in.getHeader(SmppConstants.DATA_CODING, Byte.class));
        } else if (in.getHeaders().containsKey(SmppConstants.ALPHABET)) {
            submitSm.setDataCoding(in.getHeader(SmppConstants.ALPHABET, Byte.class));
        } else {
            submitSm.setDataCoding(config.getDataCoding());
        }

        if (in.getHeaders().containsKey(SmppConstants.DEST_ADDR)) {
            submitSm.setDestAddress(in.getHeader(SmppConstants.DEST_ADDR, String.class));
        } else {
            submitSm.setDestAddress(config.getDestAddr());
        }

        if (in.getHeaders().containsKey(SmppConstants.DEST_ADDR_TON)) {
            submitSm.setDestAddrTon(in.getHeader(SmppConstants.DEST_ADDR_TON, Byte.class));
        } else {
            submitSm.setDestAddrTon(config.getDestAddrTon());
        }

        if (in.getHeaders().containsKey(SmppConstants.DEST_ADDR_NPI)) {
            submitSm.setDestAddrNpi(in.getHeader(SmppConstants.DEST_ADDR_NPI, Byte.class));
        } else {
            submitSm.setDestAddrNpi(config.getDestAddrNpi());
        }

        if (in.getHeaders().containsKey(SmppConstants.SOURCE_ADDR)) {
            submitSm.setSourceAddr(in.getHeader(SmppConstants.SOURCE_ADDR, String.class));
        } else {
            submitSm.setSourceAddr(config.getSourceAddr());
        }

        if (in.getHeaders().containsKey(SmppConstants.SOURCE_ADDR_TON)) {
            submitSm.setSourceAddrTon(in.getHeader(SmppConstants.SOURCE_ADDR_TON, Byte.class));
        } else {
            submitSm.setSourceAddrTon(config.getSourceAddrTon());
        }

        if (in.getHeaders().containsKey(SmppConstants.SOURCE_ADDR_NPI)) {
            submitSm.setSourceAddrNpi(in.getHeader(SmppConstants.SOURCE_ADDR_NPI, Byte.class));
        } else {
            submitSm.setSourceAddrNpi(config.getSourceAddrNpi());
        }

        if (in.getHeaders().containsKey(SmppConstants.SERVICE_TYPE)) {
            submitSm.setServiceType(in.getHeader(SmppConstants.SERVICE_TYPE, String.class));
        } else {
            submitSm.setServiceType(config.getServiceType());
        }

        if (in.getHeaders().containsKey(SmppConstants.REGISTERED_DELIVERY)) {
            submitSm.setRegisteredDelivery(in.getHeader(SmppConstants.REGISTERED_DELIVERY, Byte.class));
        } else {
            submitSm.setRegisteredDelivery(config.getRegisteredDelivery());
        }

        if (in.getHeaders().containsKey(SmppConstants.PROTOCOL_ID)) {
            submitSm.setProtocolId(in.getHeader(SmppConstants.PROTOCOL_ID, Byte.class));
        } else {
            submitSm.setProtocolId(config.getProtocolId());
        }

        if (in.getHeaders().containsKey(SmppConstants.PRIORITY_FLAG)) {
            submitSm.setPriorityFlag(in.getHeader(SmppConstants.PRIORITY_FLAG, Byte.class));
        } else {
            submitSm.setPriorityFlag(config.getPriorityFlag());
        }

        if (in.getHeaders().containsKey(SmppConstants.SCHEDULE_DELIVERY_TIME)) {
            submitSm.setScheduleDeliveryTime(SmppUtils.formatTime(in.getHeader(SmppConstants.SCHEDULE_DELIVERY_TIME, Date.class)));
        }

        if (in.getHeaders().containsKey(SmppConstants.VALIDITY_PERIOD)) {
            Object validityPeriod = in.getHeader(SmppConstants.VALIDITY_PERIOD);
            if (validityPeriod instanceof String) {
                submitSm.setValidityPeriod((String) validityPeriod);
            } else if (validityPeriod instanceof Date) {
                submitSm.setValidityPeriod(SmppUtils.formatTime((Date) validityPeriod));
            }
        }

        if (in.getHeaders().containsKey(SmppConstants.REPLACE_IF_PRESENT_FLAG)) {
            submitSm.setReplaceIfPresent(in.getHeader(SmppConstants.REPLACE_IF_PRESENT_FLAG, Byte.class));
        } else {
            submitSm.setReplaceIfPresent(config.getReplaceIfPresentFlag());
        }

        submitSm.setEsmClass(new ESMClass().value());

        Map<java.lang.Short, Object> optinalParamater = in.getHeader(SmppConstants.OPTIONAL_PARAMETER, Map.class);
        if (optinalParamater != null) {
            List<OptionalParameter> optParams = createOptionalParametersByCode(optinalParamater);
            submitSm.setOptionalParameters(optParams.toArray(new OptionalParameter[optParams.size()]));
        } else {
            Map<String, String> optinalParamaters = in.getHeader(SmppConstants.OPTIONAL_PARAMETERS, Map.class);
            if (optinalParamaters != null) {
                List<OptionalParameter> optParams = createOptionalParametersByName(optinalParamaters);
                submitSm.setOptionalParameters(optParams.toArray(new OptionalParameter[optParams.size()]));
            } else {
                submitSm.setOptionalParameters();
            }
        }

        return submitSm;
    }
}
