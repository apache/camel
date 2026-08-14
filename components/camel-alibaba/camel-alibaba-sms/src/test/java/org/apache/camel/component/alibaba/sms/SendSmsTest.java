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

import java.util.Map;

import com.aliyun.dysmsapi20170525.Client;
import com.aliyun.dysmsapi20170525.models.SendSmsRequest;
import com.aliyun.dysmsapi20170525.models.SendSmsResponse;
import com.aliyun.dysmsapi20170525.models.SendSmsResponseBody;
import org.apache.camel.BindToRegistry;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.alibaba.sms.constants.SMSHeaders;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SendSmsTest extends CamelTestSupport {

    private final TestConfiguration testConfiguration = new TestConfiguration();

    @BindToRegistry("smsClient")
    Client smsClient = mock(Client.class);

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:send")
                        .to("alibaba-sms:sendSms"
                            + "?phoneNumbers=" + testConfiguration.getProperty("phoneNumbers")
                            + "&signName=" + testConfiguration.getProperty("signName")
                            + "&templateCode=" + testConfiguration.getProperty("templateCode")
                            + "&region=" + testConfiguration.getProperty("region")
                            + "&accessKey=" + testConfiguration.getProperty("accessKey")
                            + "&secretKey=" + testConfiguration.getProperty("secretKey")
                            + "&smsClient=#smsClient")
                        .to("mock:result");
            }
        };
    }

    @Test
    void testSendSms() throws Exception {
        SendSmsResponseBody body = new SendSmsResponseBody();
        body.setCode("OK");
        body.setMessage("success");
        body.setBizId("biz-123");
        body.setRequestId("req-456");

        SendSmsResponse response = new SendSmsResponse();
        response.setStatusCode(200);
        response.setBody(body);

        when(smsClient.sendSms(any(SendSmsRequest.class))).thenReturn(response);

        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMinimumMessageCount(1);

        template.sendBody("direct:send", "ignored");

        mock.assertIsSatisfied();

        Exchange exchange = mock.getExchanges().get(0);
        assertThat(exchange.getMessage().getBody(Map.class))
                .containsEntry("code", "OK")
                .containsEntry("bizId", "biz-123");
        assertThat(exchange.getMessage().getHeader(SMSHeaders.REQUEST_ID)).isEqualTo("req-456");

        verify(smsClient).sendSms(any(SendSmsRequest.class));
    }
}
