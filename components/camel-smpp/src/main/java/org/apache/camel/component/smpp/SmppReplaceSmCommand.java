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

import java.util.Date;

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.jsmpp.bean.NumberingPlanIndicator;
import org.jsmpp.bean.RegisteredDelivery;
import org.jsmpp.bean.ReplaceSm;
import org.jsmpp.bean.TypeOfNumber;
import org.jsmpp.session.SMPPSession;

public class SmppReplaceSmCommand extends SmppSmCommand {

    public SmppReplaceSmCommand(SMPPSession session, SmppConfiguration config) {
        super(session, config);
    }

    @Override
    public void execute(Exchange exchange) throws SmppException {
        byte[] message = getShortMessage(exchange.getIn());

        ReplaceSm replaceSm = createReplaceSmTempate(exchange);
        replaceSm.setShortMessage(message);

        if (log.isDebugEnabled()) {
            log.debug("Sending replacement command for a short message for exchange id '{}' and message id '{}'",
                    exchange.getExchangeId(), replaceSm.getMessageId());
        }

        try {
            session.replaceShortMessage(
                    replaceSm.getMessageId(),
                    TypeOfNumber.valueOf(replaceSm.getSourceAddrTon()),
                    NumberingPlanIndicator.valueOf(replaceSm.getSourceAddrNpi()),
                    replaceSm.getSourceAddr(),
                    replaceSm.getScheduleDeliveryTime(),
                    replaceSm.getValidityPeriod(),
                    new RegisteredDelivery(replaceSm.getRegisteredDelivery()),
                    replaceSm.getSmDefaultMsgId(),
                    replaceSm.getShortMessage());
        } catch (Exception e) {
            throw new SmppException(e);
        }

        if (log.isDebugEnabled()) {
            log.debug("Sent replacement command for a short message for exchange id '{}' and message id '{}'",
                    exchange.getExchangeId(), replaceSm.getMessageId());
        }
        
        Message rspMsg = getResponseMessage(exchange);
        rspMsg.setHeader(SmppConstants.ID, replaceSm.getMessageId());
    }

    protected ReplaceSm createReplaceSmTempate(Exchange exchange) {
        Message in = exchange.getIn();
        ReplaceSm replaceSm = new ReplaceSm();

        if (in.getHeaders().containsKey(SmppConstants.ID)) {
            replaceSm.setMessageId(in.getHeader(SmppConstants.ID, String.class));
        }

        if (in.getHeaders().containsKey(SmppConstants.SOURCE_ADDR)) {
            replaceSm.setSourceAddr(in.getHeader(SmppConstants.SOURCE_ADDR, String.class));
        } else {
            replaceSm.setSourceAddr(config.getSourceAddr());
        }

        if (in.getHeaders().containsKey(SmppConstants.SOURCE_ADDR_TON)) {
            replaceSm.setSourceAddrTon(in.getHeader(SmppConstants.SOURCE_ADDR_TON, Byte.class));
        } else {
            replaceSm.setSourceAddrTon(config.getSourceAddrTon());
        }

        if (in.getHeaders().containsKey(SmppConstants.SOURCE_ADDR_NPI)) {
            replaceSm.setSourceAddrNpi(in.getHeader(SmppConstants.SOURCE_ADDR_NPI, Byte.class));
        } else {
            replaceSm.setSourceAddrNpi(config.getSourceAddrNpi());
        }

        if (in.getHeaders().containsKey(SmppConstants.REGISTERED_DELIVERY)) {
            replaceSm.setRegisteredDelivery(in.getHeader(SmppConstants.REGISTERED_DELIVERY, Byte.class));
        } else {
            replaceSm.setRegisteredDelivery(config.getRegisteredDelivery());
        }

        if (in.getHeaders().containsKey(SmppConstants.SCHEDULE_DELIVERY_TIME)) {
            replaceSm.setScheduleDeliveryTime(SmppUtils.formatTime(in.getHeader(SmppConstants.SCHEDULE_DELIVERY_TIME, Date.class)));
        }

        if (in.getHeaders().containsKey(SmppConstants.VALIDITY_PERIOD)) {
            Object validityPeriod = in.getHeader(SmppConstants.VALIDITY_PERIOD);
            if (validityPeriod instanceof String) {
                replaceSm.setValidityPeriod((String) validityPeriod);
            } else if (validityPeriod instanceof Date) {
                replaceSm.setValidityPeriod(SmppUtils.formatTime((Date) validityPeriod));
            }
        }

        return replaceSm;
    }
}