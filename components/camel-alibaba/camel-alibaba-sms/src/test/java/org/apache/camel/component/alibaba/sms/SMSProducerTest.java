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
package org.apache.camel.component.alibaba.sms;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import org.apache.camel.support.DefaultExchange;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SMSProducerTest extends CamelTestSupport {

    @Test
    void processReinitializesClientFromEndpointOnEachExchange() throws Exception {
        SMSEndpoint endpoint = mock(SMSEndpoint.class);
        when(endpoint.getOperation()).thenReturn("sendSms");
        when(endpoint.getPhoneNumbers()).thenReturn("13800138000");
        when(endpoint.getSignName()).thenReturn("demo-sign");
        when(endpoint.getTemplateCode()).thenReturn("SMS_123456");
        when(endpoint.getCamelContext()).thenReturn(context);

        Client firstClient = mock(Client.class);
        Client secondClient = mock(Client.class);
        when(endpoint.initClient()).thenReturn(firstClient, secondClient);

        SendSmsResponse response = new SendSmsResponse();
        when(firstClient.sendSms(any(SendSmsRequest.class))).thenReturn(response);
        when(secondClient.sendSms(any(SendSmsRequest.class))).thenReturn(response);

        SMSProducer producer = new SMSProducer(endpoint);
        producer.process(new DefaultExchange(context));
        producer.process(new DefaultExchange(context));

        verify(firstClient).sendSms(any(SendSmsRequest.class));
        verify(secondClient).sendSms(any(SendSmsRequest.class));
    }
}
