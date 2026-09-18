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
import org.apache.camel.Exchange;
import org.apache.camel.component.alibaba.sms.constants.SMSHeaders;
import org.apache.camel.component.alibaba.sms.constants.SMSOperations;
import org.apache.camel.component.alibaba.sms.models.ClientConfigurations;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;

public class SMSProducer extends DefaultProducer {

    private Client smsClient;

    public SMSProducer(SMSEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        SMSEndpoint endpoint = getEndpoint();
        ClientConfigurations configuration = SMSUtils.createClientConfigurations(endpoint, exchange);

        if (ObjectHelper.isEmpty(configuration.operation())) {
            throw new IllegalArgumentException("Operation name not found");
        }

        if (smsClient == null) {
            smsClient = endpoint.initClient();
        }

        switch (configuration.operation()) {
            case SMSOperations.SEND_SMS -> sendSms(exchange, configuration);
            default -> throw new UnsupportedOperationException("Unsupported operation: " + configuration.operation());
        }
    }

    private void sendSms(Exchange exchange, ClientConfigurations configuration) throws Exception {
        if (ObjectHelper.isEmpty(configuration.phoneNumbers())
                || ObjectHelper.isEmpty(configuration.signName())
                || ObjectHelper.isEmpty(configuration.templateCode())) {
            throw new IllegalArgumentException("Phone numbers, sign name and template code are required for sendSms");
        }

        SendSmsRequest request = new SendSmsRequest()
                .setPhoneNumbers(configuration.phoneNumbers())
                .setSignName(configuration.signName())
                .setTemplateCode(configuration.templateCode());

        if (ObjectHelper.isNotEmpty(configuration.templateParam())) {
            request.setTemplateParam(configuration.templateParam());
        }
        if (ObjectHelper.isNotEmpty(configuration.outId())) {
            request.setOutId(configuration.outId());
        }

        SendSmsResponse response = smsClient.sendSms(request);
        exchange.getMessage().setBody(SMSUtils.toSendSmsMap(response));
        if (response.getBody() != null && ObjectHelper.isNotEmpty(response.getBody().getRequestId())) {
            exchange.getMessage().setHeader(SMSHeaders.REQUEST_ID, response.getBody().getRequestId());
        }
    }

    @Override
    public SMSEndpoint getEndpoint() {
        return (SMSEndpoint) super.getEndpoint();
    }
}
