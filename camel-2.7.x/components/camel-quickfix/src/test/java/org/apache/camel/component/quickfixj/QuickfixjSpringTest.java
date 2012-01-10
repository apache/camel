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
package org.apache.camel.component.quickfixj;

import java.util.Properties;

import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import quickfix.DefaultMessageFactory;
import quickfix.FixVersions;
import quickfix.Message;
import quickfix.SessionID;
import quickfix.SessionSettings;
import quickfix.field.MsgType;
import quickfix.fix42.NewOrderSingle;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
public class QuickfixjSpringTest {
    @Autowired(required = true)
    private QuickfixjComponent component;

    @Autowired(required = true)
    private SessionSettings springSessionSettings;

    @Test
    public void configureInSpring() throws Exception {
        SessionID sessionID = new SessionID("FIX.4.2:INITIATOR->ACCEPTOR");
        Properties sessionProperties = springSessionSettings.getSessionProperties(sessionID, true);
        Assert.assertThat(sessionProperties.get("ConnectionType").toString(), CoreMatchers.is("initiator"));
        Assert.assertThat(sessionProperties.get("SocketConnectProtocol").toString(), CoreMatchers.is("VM_PIPE"));

        QuickfixjEngine engine = component.getEngines().values().iterator().next();

        Assert.assertThat(engine.getMessageFactory(), is(instanceOf(CustomMessageFactory.class)));
    }

    /**
     * Customer message factory and message class for test purposes
     */
    public static class CustomMessageFactory extends DefaultMessageFactory {
        @Override
        public Message create(String beginString, String msgType) {
            if (beginString.equals(FixVersions.BEGINSTRING_FIX42) && msgType.equals(MsgType.ORDER_SINGLE)) {
                return new CustomNewOrderSingle();
            }
            return super.create(beginString, msgType);
        }
    }

    @SuppressWarnings("serial")
    public static class CustomNewOrderSingle extends NewOrderSingle {
    }
}
