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
import org.jsmpp.session.SMPPSession;

public enum SmppCommandType {

    SUBMIT_SM("SubmitSm") {
        @Override
        public SmppSubmitSmCommand createCommand(SMPPSession session, SmppConfiguration config) {
            return new SmppSubmitSmCommand(session, config);
        }
    },
    REPLACE_SM("ReplaceSm") {
        @Override
        public SmppReplaceSmCommand createCommand(SMPPSession session, SmppConfiguration config) {
            return new SmppReplaceSmCommand(session, config);
        }
    },
    QUERY_SM("QuerySm") {
        @Override
        public SmppQuerySmCommand createCommand(SMPPSession session, SmppConfiguration config) {
            return new SmppQuerySmCommand(session, config);
        }
    },
    SUBMIT_MULTI("SubmitMulti") {
        @Override
        public SmppSubmitMultiCommand createCommand(SMPPSession session, SmppConfiguration config) {
            return new SmppSubmitMultiCommand(session, config);
        }
    },
    CANCEL_SM("CancelSm") {
        @Override
        public SmppCancelSmCommand createCommand(SMPPSession session, SmppConfiguration config) {
            return new SmppCancelSmCommand(session, config);
        }
    },
    DATA_SHORT_MESSAGE("DataSm") {
        @Override
        public SmppDataSmCommand createCommand(SMPPSession session, SmppConfiguration config) {
            return new SmppDataSmCommand(session, config);
        }
    };

    private String commandName;

    SmppCommandType(String commandName) {
        this.commandName = commandName;
    }

    public String getCommandName() {
        return commandName;
    }

    public abstract SmppCommand createCommand(SMPPSession session, SmppConfiguration config);

    /**
     * Tries to return an instance of {@link SmppCommandType} using
     * {@link SmppConstants#COMMAND} header of the incoming message.
     * <p/>
     * Returns {@link #SUBMIT_SM} if there is no {@link SmppConstants#COMMAND}
     * header in the incoming message or value of such a header cannot be
     * recognized.
     * <p/>
     * The valid values for the {@link SmppConstants#COMMAND} header are: <span
     * style="font: bold;">SubmitSm</span> <span
     * style="font: bold;">ReplaceSm</span>, <span
     * style="font: bold;">QuerySm</span>, <span
     * style="font: bold;">SubmitMulti</span>, <span
     * style="font: bold;">CancelSm</span>, <span
     * style="font: bold;">DataSm</span>.
     * 
     * @param exchange
     *            an exchange to get an incoming message from
     * @return an instance of {@link SmppCommandType}
     */
    public static SmppCommandType fromExchange(Exchange exchange) {
        Message in = exchange.getIn();

        String commandName = null;
        if (in.getHeaders().containsKey(SmppConstants.COMMAND)) {
            commandName = in.getHeader(SmppConstants.COMMAND, String.class);
        }

        SmppCommandType commandType = SUBMIT_SM;
        for (SmppCommandType nextCommandType : values()) {
            if (nextCommandType.commandName.equals(commandName)) {
                commandType = nextCommandType;
                break;
            }
        }

        return commandType;
    }
}