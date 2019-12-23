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
package org.apache.camel.example.telegram.usage;

import org.apache.camel.CamelContext;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.component.telegram.model.EditMessageLiveLocationMessage;
import org.apache.camel.component.telegram.model.MessageResult;
import org.apache.camel.component.telegram.model.SendLocationMessage;
import org.apache.camel.component.telegram.model.StopMessageLiveLocationMessage;

public class LiveLocationUsage implements TelegramMethodUsage {

    private double latitude = 59.9386292;
    private double longitude = 30.3141308;

    @Override
    public void run(CamelContext context) throws InterruptedException {
        ProducerTemplate template = context.createProducerTemplate();
        SendLocationMessage msg = new SendLocationMessage(latitude, longitude);
        msg.setLivePeriod(new Integer(60));
        MessageResult firstLocationMessage = template.requestBody("direct:start", msg, MessageResult.class);
        System.out.println(firstLocationMessage);

        long messageId = firstLocationMessage.getMessage().getMessageId();

        double delta = 0.001;
        for (int i = 0; i < 3; i++) {
            double positionDelta = delta * (i + 1);
            EditMessageLiveLocationMessage liveLocationMessage = new EditMessageLiveLocationMessage(latitude + positionDelta, longitude + positionDelta);
            liveLocationMessage.setMessageId(messageId);
            MessageResult editedMessage = template.requestBody("direct:start", liveLocationMessage, MessageResult.class);
            System.out.println(editedMessage);
            Thread.sleep(3000);
        }

        StopMessageLiveLocationMessage stopLiveLocationMessage = new StopMessageLiveLocationMessage();
        stopLiveLocationMessage.setMessageId(messageId);
        MessageResult stopMessage = template.requestBody("direct:start", stopLiveLocationMessage, MessageResult.class);
        System.out.println(stopMessage);
    }
}
