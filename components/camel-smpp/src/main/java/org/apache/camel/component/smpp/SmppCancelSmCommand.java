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

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.jsmpp.bean.CancelSm;
import org.jsmpp.bean.NumberingPlanIndicator;
import org.jsmpp.bean.TypeOfNumber;
import org.jsmpp.session.SMPPSession;

public class SmppCancelSmCommand extends AbstractSmppCommand {

    public SmppCancelSmCommand(SMPPSession session, SmppConfiguration config) {
        super(session, config);
    }

    @Override
    public void execute(Exchange exchange) throws SmppException {
        CancelSm cancelSm = createCancelSm(exchange);

        if (log.isDebugEnabled()) {
            log.debug("Canceling a short message for exchange id '{}' and message id '{}'",
                    exchange.getExchangeId(), cancelSm.getMessageId());
        }

        try {
            session.cancelShortMessage(
                    cancelSm.getServiceType(),
                    cancelSm.getMessageId(),
                    TypeOfNumber.valueOf(cancelSm.getSourceAddrTon()),
                    NumberingPlanIndicator.valueOf(cancelSm.getSourceAddrNpi()),
                    cancelSm.getSourceAddr(),
                    TypeOfNumber.valueOf(cancelSm.getDestAddrTon()),
                    NumberingPlanIndicator.valueOf(cancelSm.getDestAddrNpi()),
                    cancelSm.getDestinationAddress());
        } catch (Exception e) {
            throw new SmppException(e);
        }

        if (log.isDebugEnabled()) {
            log.debug("Cancel a short message for exchange id '{}' and message id '{}'",
                    exchange.getExchangeId(), cancelSm.getMessageId());
        }

        Message message = getResponseMessage(exchange);
        message.setHeader(SmppConstants.ID, cancelSm.getMessageId());
    }

    protected CancelSm createCancelSm(Exchange exchange) {
        Message in = exchange.getIn();
        CancelSm cancelSm = new CancelSm();

        if (in.getHeaders().containsKey(SmppConstants.ID)) {
            cancelSm.setMessageId(in.getHeader(SmppConstants.ID, String.class));
        }

        if (in.getHeaders().containsKey(SmppConstants.SOURCE_ADDR)) {
            cancelSm.setSourceAddr(in.getHeader(SmppConstants.SOURCE_ADDR, String.class));
        } else {
            cancelSm.setSourceAddr(config.getSourceAddr());
        }

        if (in.getHeaders().containsKey(SmppConstants.SOURCE_ADDR_TON)) {
            cancelSm.setSourceAddrTon(in.getHeader(SmppConstants.SOURCE_ADDR_TON, Byte.class));
        } else {
            cancelSm.setSourceAddrTon(config.getSourceAddrTon());
        }

        if (in.getHeaders().containsKey(SmppConstants.SOURCE_ADDR_NPI)) {
            cancelSm.setSourceAddrNpi(in.getHeader(SmppConstants.SOURCE_ADDR_NPI, Byte.class));
        } else {
            cancelSm.setSourceAddrNpi(config.getSourceAddrNpi());
        }

        if (in.getHeaders().containsKey(SmppConstants.DEST_ADDR)) {
            cancelSm.setDestinationAddress(in.getHeader(SmppConstants.DEST_ADDR, String.class));
        } else {
            cancelSm.setDestinationAddress(config.getDestAddr());
        }

        if (in.getHeaders().containsKey(SmppConstants.DEST_ADDR_TON)) {
            cancelSm.setDestAddrTon(in.getHeader(SmppConstants.DEST_ADDR_TON, Byte.class));
        } else {
            cancelSm.setDestAddrTon(config.getDestAddrTon());
        }

        if (in.getHeaders().containsKey(SmppConstants.DEST_ADDR_NPI)) {
            cancelSm.setDestAddrNpi(in.getHeader(SmppConstants.DEST_ADDR_NPI, Byte.class));
        } else {
            cancelSm.setDestAddrNpi(config.getDestAddrNpi());
        }

        if (in.getHeaders().containsKey(SmppConstants.SERVICE_TYPE)) {
            cancelSm.setServiceType(in.getHeader(SmppConstants.SERVICE_TYPE, String.class));
        } else {
            cancelSm.setServiceType(config.getServiceType());
        }

        return cancelSm;
    }
}