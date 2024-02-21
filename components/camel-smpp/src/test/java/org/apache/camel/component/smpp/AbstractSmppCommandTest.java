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
import org.apache.camel.ExchangePattern;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.support.ExchangeHelper;
import org.jsmpp.bean.OptionalParameter;
import org.jsmpp.bean.OptionalParameter.Tag;
import org.jsmpp.session.SMPPSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertSame;

public class AbstractSmppCommandTest {

    private SMPPSession session = new SMPPSession();
    private SmppConfiguration config = new SmppConfiguration();
    private AbstractSmppCommand command;

    @BeforeEach
    public void setUp() {
        session = new SMPPSession();
        config = new SmppConfiguration();

        command = new AbstractSmppCommand(session, config) {
            @Override
            public void execute(Exchange exchange) {
            }
        };
    }

    @Test
    public void constructor() {
        assertSame(session, command.session);
        assertSame(config, command.config);
    }

    @Test
    public void getResponseMessage() {
        Exchange inOnlyExchange = new DefaultExchange(new DefaultCamelContext(), ExchangePattern.InOnly);
        Exchange inOutExchange = new DefaultExchange(new DefaultCamelContext(), ExchangePattern.InOut);

        assertSame(ExchangeHelper.getResultMessage(inOnlyExchange), inOnlyExchange.getIn());
        /*
          NOTE: in this test it's important to call the methods in this order:
          1. command.getResponseMessage
          2. inOutExchange.getMessage

          This is so, because the empty out Message object is created by the getOut messaged called by
          command.getResponseMessage. Calling in the inverse order causes the hasOut check on getMessage()
          to return false, which, in turns, causes it to return the in message. Thus failing the test.
         */
        Message expectedMessage = ExchangeHelper.getResultMessage(inOutExchange);
        Message verificationMessage = inOutExchange.getMessage();

        assertSame(expectedMessage, verificationMessage);
    }

    @Test
    public void determineTypeClass() throws Exception {
        assertSame(OptionalParameter.Source_subaddress.class, command.determineTypeClass(Tag.SOURCE_SUBADDRESS));
        assertSame(OptionalParameter.Additional_status_info_text.class,
                command.determineTypeClass(Tag.ADDITIONAL_STATUS_INFO_TEXT));
        assertSame(OptionalParameter.Dest_addr_subunit.class, command.determineTypeClass(Tag.DEST_ADDR_SUBUNIT));
        assertSame(OptionalParameter.Dest_telematics_id.class, command.determineTypeClass(Tag.DEST_TELEMATICS_ID));
        assertSame(OptionalParameter.Qos_time_to_live.class, command.determineTypeClass(Tag.QOS_TIME_TO_LIVE));
        assertSame(OptionalParameter.Alert_on_message_delivery.class,
                command.determineTypeClass(Tag.ALERT_ON_MESSAGE_DELIVERY));
    }
}
