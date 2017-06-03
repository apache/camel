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

import org.apache.camel.test.spring.CamelSpringTestSupport;
import org.hamcrest.CoreMatchers;
import org.junit.Test;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import quickfix.DefaultMessageFactory;
import quickfix.FixVersions;
import quickfix.Message;
import quickfix.SessionID;
import quickfix.SessionSettings;
import quickfix.field.MsgType;
import quickfix.fix42.NewOrderSingle;

import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.is;

public class QuickfixjSpringTest extends CamelSpringTestSupport {

    @Override
    public void setUp() throws Exception {
        if (isJava16()) {
            // cannot test on java 1.6
            return;
        }
        super.setUp();
    }

    @Override
    protected AbstractApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/quickfixj/QuickfixjSpringTest-context.xml");
    }

    @Test
    public void configureInSpring() throws Exception {
        if (isJava16()) {
            // cannot test on java 1.6
            return;
        }

        SessionID sessionID = new SessionID("FIX.4.2:INITIATOR->ACCEPTOR");
        QuickfixjConfiguration configuration = context.getRegistry().lookupByNameAndType("quickfixjConfiguration", QuickfixjConfiguration.class);

        SessionSettings springSessionSettings = configuration.createSessionSettings();
        Properties sessionProperties = springSessionSettings.getSessionProperties(sessionID, true);

        assertThat(sessionProperties.get("ConnectionType").toString(), CoreMatchers.is("initiator"));
        assertThat(sessionProperties.get("SocketConnectProtocol").toString(), CoreMatchers.is("VM_PIPE"));

        QuickfixjComponent component = context.getComponent("quickfix", QuickfixjComponent.class);
        assertThat(component.isLazyCreateEngines(), is(false));
        QuickfixjEngine engine = component.getEngines().values().iterator().next();
        assertThat(engine.isInitialized(), is(true));

        QuickfixjComponent lazyComponent = context.getComponent("lazyQuickfix", QuickfixjComponent.class);
        assertThat(lazyComponent.isLazyCreateEngines(), is(true));
        QuickfixjEngine lazyEngine = lazyComponent.getEngines().values().iterator().next();
        assertThat(lazyEngine.isInitialized(), is(false));

        assertThat(engine.getMessageFactory(), is(instanceOf(CustomMessageFactory.class)));
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

    public static class CustomNewOrderSingle extends NewOrderSingle {
        private static final long serialVersionUID = 1L;
    }
}
