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

import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.jsmpp.bean.NumberingPlanIndicator;
import org.jsmpp.bean.QuerySm;
import org.jsmpp.bean.TypeOfNumber;
import org.jsmpp.session.QuerySmResult;
import org.jsmpp.session.SMPPSession;

public class SmppQuerySmCommand extends AbstractSmppCommand {

    public SmppQuerySmCommand(SMPPSession session, SmppConfiguration config) {
        super(session, config);
    }

    @Override
    public void execute(Exchange exchange) throws SmppException {
        QuerySm querySm = createQuerySm(exchange);

        if (log.isDebugEnabled()) {
            log.debug("Querying for a short message for exchange id '{}' and message id '{}'...",
                    exchange.getExchangeId(), querySm.getMessageId());
        }
        
        QuerySmResult querySmResult;
        try {
            querySmResult = session.queryShortMessage(
                    querySm.getMessageId(),
                    TypeOfNumber.valueOf(querySm.getSourceAddrTon()),
                    NumberingPlanIndicator.valueOf(querySm.getSourceAddrNpi()),
                    querySm.getSourceAddr());
        } catch (Exception e) {
            throw new SmppException(e);
        }

        if (log.isDebugEnabled()) {
            log.debug("Query for a short message for exchange id '{}' and message id '{}'",
                    exchange.getExchangeId(), querySm.getMessageId());
        }

        Message message = getResponseMessage(exchange);
        message.setHeader(SmppConstants.ID, querySm.getMessageId());
        message.setHeader(SmppConstants.ERROR, querySmResult.getErrorCode());
        message.setHeader(SmppConstants.FINAL_DATE, SmppUtils.string2Date(querySmResult.getFinalDate()));
        message.setHeader(SmppConstants.MESSAGE_STATE, querySmResult.getMessageState().name());
    }

    protected QuerySm createQuerySm(Exchange exchange) {
        Message in = exchange.getIn();
        QuerySm querySm = new QuerySm();

        if (in.getHeaders().containsKey(SmppConstants.ID)) {
            querySm.setMessageId(in.getHeader(SmppConstants.ID, String.class));
        }

        if (in.getHeaders().containsKey(SmppConstants.SOURCE_ADDR)) {
            querySm.setSourceAddr(in.getHeader(SmppConstants.SOURCE_ADDR, String.class));
        } else {
            querySm.setSourceAddr(config.getSourceAddr());
        }

        if (in.getHeaders().containsKey(SmppConstants.SOURCE_ADDR_TON)) {
            querySm.setSourceAddrTon(in.getHeader(SmppConstants.SOURCE_ADDR_TON, Byte.class));
        } else {
            querySm.setSourceAddrTon(config.getSourceAddrTon());
        }

        if (in.getHeaders().containsKey(SmppConstants.SOURCE_ADDR_NPI)) {
            querySm.setSourceAddrNpi(in.getHeader(SmppConstants.SOURCE_ADDR_NPI, Byte.class));
        } else {
            querySm.setSourceAddrNpi(config.getSourceAddrNpi());
        }

        return querySm;
    }
}