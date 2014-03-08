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
package org.apache.camel.component.spring.ws.addressing;

import java.net.URISyntaxException;

import org.springframework.context.support.AbstractXmlApplicationContext;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.ws.soap.addressing.client.ActionCallback;
import org.springframework.ws.soap.addressing.core.MessageAddressingProperties;

public class ConsumerWSANewChannelParamsActionTests extends AbstractConsumerTests {

    public ActionCallback channelIn(String actionUri) throws URISyntaxException {
        // new channel
        return actionAndReplyTo(actionUri, "mailto:reply-to-trigger@new-channel.com");
    }

    @Override
    public MessageAddressingProperties channelOut() {
        return newChannelParams();
    }

    @Override
    protected AbstractXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext(new String[] {"org/apache/camel/component/spring/ws/addresing/ConsumerWSAParamsActionTests-context.xml"});
    }

}
