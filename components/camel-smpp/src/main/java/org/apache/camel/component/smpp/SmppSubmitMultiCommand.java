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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.support.ExchangeHelper;
import org.jsmpp.bean.Address;
import org.jsmpp.bean.DataCodings;
import org.jsmpp.bean.ESMClass;
import org.jsmpp.bean.GSMSpecificFeature;
import org.jsmpp.bean.MessageMode;
import org.jsmpp.bean.MessageType;
import org.jsmpp.bean.NumberingPlanIndicator;
import org.jsmpp.bean.OptionalParameter;
import org.jsmpp.bean.RegisteredDelivery;
import org.jsmpp.bean.ReplaceIfPresentFlag;
import org.jsmpp.bean.SMSCDeliveryReceipt;
import org.jsmpp.bean.SubmitMulti;
import org.jsmpp.bean.TypeOfNumber;
import org.jsmpp.bean.UnsuccessDelivery;
import org.jsmpp.session.SMPPSession;
import org.jsmpp.session.SubmitMultiResult;

public class SmppSubmitMultiCommand extends SmppSmCommand {

    public SmppSubmitMultiCommand(SMPPSession session, SmppConfiguration config) {
        super(session, config);
    }

    @Override
    public void execute(Exchange exchange) throws SmppException {
        SubmitMulti[] submitMulties = createSubmitMulti(exchange);
        List<SubmitMultiResult> results = new ArrayList<>(submitMulties.length);

        for (SubmitMulti submitMulti : submitMulties) {
            SubmitMultiResult result;
            if (log.isDebugEnabled()) {
                log.debug("Sending multiple short messages for exchange id '{}'...", exchange.getExchangeId());
            }

            try {
                result = session.submitMultiple(
                        submitMulti.getServiceType(),
                        TypeOfNumber.valueOf(submitMulti.getSourceAddrTon()),
                        NumberingPlanIndicator.valueOf(submitMulti.getSourceAddrNpi()),
                        submitMulti.getSourceAddr(),
                        (Address[]) submitMulti.getDestAddresses(),
                        new ESMClass(submitMulti.getEsmClass()),
                        submitMulti.getProtocolId(),
                        submitMulti.getPriorityFlag(),
                        submitMulti.getScheduleDeliveryTime(),
                        submitMulti.getValidityPeriod(),
                        new RegisteredDelivery(submitMulti.getRegisteredDelivery()),
                        new ReplaceIfPresentFlag(submitMulti.getReplaceIfPresentFlag()),
                        DataCodings.newInstance(submitMulti.getDataCoding()),
                        submitMulti.getSmDefaultMsgId(),
                        submitMulti.getShortMessage(),
                        submitMulti.getOptionalParameters());
                results.add(result);
            } catch (Exception e) {
                throw new SmppException(e);
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("Sent multiple short messages for exchange id '{}' and received results '{}'", exchange.getExchangeId(),
                    results);
        }

        List<String> messageIDs = new ArrayList<>(results.size());
        // {messageID : [{destAddr : address, error : errorCode}]}
        Map<String, List<Map<String, Object>>> errors = new HashMap<>();

        for (SubmitMultiResult result : results) {
            UnsuccessDelivery[] deliveries = result.getUnsuccessDeliveries();

            if (deliveries != null) {
                List<Map<String, Object>> undelivered = new ArrayList<>();

                for (UnsuccessDelivery delivery : deliveries) {
                    Map<String, Object> error = new HashMap<>();
                    error.put(SmppConstants.DEST_ADDR, delivery.getDestinationAddress().getAddress());
                    error.put(SmppConstants.ERROR, delivery.getErrorStatusCode());
                    undelivered.add(error);
                }

                if (!undelivered.isEmpty()) {
                    errors.put(result.getMessageId(), undelivered);
                }
            }

            messageIDs.add(result.getMessageId());
        }

        Message message = ExchangeHelper.getResultMessage(exchange);
        message.setHeader(SmppConstants.ID, messageIDs);
        message.setHeader(SmppConstants.SENT_MESSAGE_COUNT, messageIDs.size());
        if (!errors.isEmpty()) {
            message.setHeader(SmppConstants.ERROR, errors);
        }
    }

    protected SubmitMulti[] createSubmitMulti(Exchange exchange) throws SmppException {
        Message message = exchange.getIn();
        byte[][] segments = splitBody(message);
        SubmitMulti template = createSubmitMultiTemplate(exchange);

        // FIXME: undocumented header
        ESMClass esmClass = message.getHeader(SmppConstants.ESM_CLASS, ESMClass.class);
        if (esmClass != null) {
            template.setEsmClass(esmClass.value());
        } else if (segments.length > 1) {
            // multipart message
            template.setEsmClass(new ESMClass(MessageMode.DEFAULT, MessageType.DEFAULT, GSMSpecificFeature.UDHI).value());
        }

        SubmitMulti[] submitMulties = new SubmitMulti[segments.length];
        for (int i = 0; i < segments.length; i++) {
            SubmitMulti submitMulti = SmppUtils.copySubmitMulti(template);
            submitMulti.setShortMessage(segments[i]);
            submitMulties[i] = submitMulti;
        }

        setRegisterDeliveryReceiptFlag(submitMulties, message);
        return submitMulties;
    }

    protected void setRegisterDeliveryReceiptFlag(SubmitMulti[] submitMulties, Message message) {
        byte specifiedDeliveryFlag = getRegisterDeliveryFlag(message);
        byte flag;
        if (getRequestsSingleDLR(message)) {
            // Disable DLRs
            flag = SMSCDeliveryReceipt.DEFAULT.value();
        } else {
            flag = specifiedDeliveryFlag;
        }

        for (int i = 0; i < submitMulties.length - 1; i++) {
            submitMulties[i].setRegisteredDelivery(flag);
        }
        submitMulties[submitMulties.length - 1].setRegisteredDelivery(specifiedDeliveryFlag);
    }

    @SuppressWarnings({ "unchecked" })
    protected SubmitMulti createSubmitMultiTemplate(Exchange exchange) {
        Message in = exchange.getIn();
        SubmitMulti submitMulti = new SubmitMulti();

        if (in.getHeaders().containsKey(SmppConstants.DATA_CODING)) {
            submitMulti.setDataCoding(in.getHeader(SmppConstants.DATA_CODING, Byte.class));
        } else if (in.getHeaders().containsKey(SmppConstants.ALPHABET)) {
            submitMulti.setDataCoding(in.getHeader(SmppConstants.ALPHABET, Byte.class));
        } else {
            submitMulti.setDataCoding(config.getDataCoding());
        }

        byte destAddrTon;
        if (in.getHeaders().containsKey(SmppConstants.DEST_ADDR_TON)) {
            destAddrTon = in.getHeader(SmppConstants.DEST_ADDR_TON, Byte.class);
        } else {
            destAddrTon = config.getDestAddrTon();
        }

        byte destAddrNpi;
        if (in.getHeaders().containsKey(SmppConstants.DEST_ADDR_NPI)) {
            destAddrNpi = in.getHeader(SmppConstants.DEST_ADDR_NPI, Byte.class);
        } else {
            destAddrNpi = config.getDestAddrNpi();
        }

        List<String> destAddresses;
        if (in.getHeaders().containsKey(SmppConstants.DEST_ADDR)) {
            destAddresses = in.getHeader(SmppConstants.DEST_ADDR, List.class);
        } else {
            destAddresses = Arrays.asList(config.getDestAddr());
        }

        Address[] addresses = new Address[destAddresses.size()];
        int addrNum = 0;
        for (String destAddr : destAddresses) {
            Address addr = new Address(destAddrTon, destAddrNpi, destAddr);
            addresses[addrNum++] = addr;
        }
        submitMulti.setDestAddresses(addresses);

        if (in.getHeaders().containsKey(SmppConstants.SOURCE_ADDR)) {
            submitMulti.setSourceAddr(in.getHeader(SmppConstants.SOURCE_ADDR, String.class));
        } else {
            submitMulti.setSourceAddr(config.getSourceAddr());
        }

        if (in.getHeaders().containsKey(SmppConstants.SOURCE_ADDR_TON)) {
            submitMulti.setSourceAddrTon(in.getHeader(SmppConstants.SOURCE_ADDR_TON, Byte.class));
        } else {
            submitMulti.setSourceAddrTon(config.getSourceAddrTon());
        }

        if (in.getHeaders().containsKey(SmppConstants.SOURCE_ADDR_NPI)) {
            submitMulti.setSourceAddrNpi(in.getHeader(SmppConstants.SOURCE_ADDR_NPI, Byte.class));
        } else {
            submitMulti.setSourceAddrNpi(config.getSourceAddrNpi());
        }

        if (in.getHeaders().containsKey(SmppConstants.SERVICE_TYPE)) {
            submitMulti.setServiceType(in.getHeader(SmppConstants.SERVICE_TYPE, String.class));
        } else {
            submitMulti.setServiceType(config.getServiceType());
        }

        if (in.getHeaders().containsKey(SmppConstants.PROTOCOL_ID)) {
            submitMulti.setProtocolId(in.getHeader(SmppConstants.PROTOCOL_ID, Byte.class));
        } else {
            submitMulti.setProtocolId(config.getProtocolId());
        }

        if (in.getHeaders().containsKey(SmppConstants.PRIORITY_FLAG)) {
            submitMulti.setPriorityFlag(in.getHeader(SmppConstants.PRIORITY_FLAG, Byte.class));
        } else {
            submitMulti.setPriorityFlag(config.getPriorityFlag());
        }

        if (in.getHeaders().containsKey(SmppConstants.SCHEDULE_DELIVERY_TIME)) {
            submitMulti.setScheduleDeliveryTime(
                    SmppUtils.formatTime(in.getHeader(SmppConstants.SCHEDULE_DELIVERY_TIME, Date.class)));
        }

        if (in.getHeaders().containsKey(SmppConstants.VALIDITY_PERIOD)) {
            Object validityPeriod = in.getHeader(SmppConstants.VALIDITY_PERIOD);
            if (validityPeriod instanceof String) {
                submitMulti.setValidityPeriod((String) validityPeriod);
            } else if (validityPeriod instanceof Date) {
                submitMulti.setValidityPeriod(SmppUtils.formatTime((Date) validityPeriod));
            }
        }

        if (in.getHeaders().containsKey(SmppConstants.REPLACE_IF_PRESENT_FLAG)) {
            submitMulti.setReplaceIfPresentFlag(in.getHeader(SmppConstants.REPLACE_IF_PRESENT_FLAG, Byte.class));
        } else {
            submitMulti.setReplaceIfPresentFlag(config.getReplaceIfPresentFlag());
        }

        Map<java.lang.Short, Object> optinalParamater = in.getHeader(SmppConstants.OPTIONAL_PARAMETER, Map.class);
        if (optinalParamater != null) {
            List<OptionalParameter> optParams = createOptionalParametersByCode(optinalParamater);
            submitMulti.setOptionalParameters(optParams.toArray(new OptionalParameter[0]));
        } else {
            Map<String, String> optinalParamaters = in.getHeader(SmppConstants.OPTIONAL_PARAMETERS, Map.class);
            if (optinalParamaters != null) {
                List<OptionalParameter> optParams = createOptionalParametersByName(optinalParamaters);
                submitMulti.setOptionalParameters(optParams.toArray(new OptionalParameter[0]));
            } else {
                submitMulti.setOptionalParameters(new OptionalParameter[] {});
            }
        }

        return submitMulti;
    }
}
