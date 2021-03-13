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
package org.apache.camel.component.aws2.ses;

import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SendEmailResponse;
import software.amazon.awssdk.services.ses.model.SendRawEmailRequest;
import software.amazon.awssdk.services.ses.model.SendRawEmailResponse;

public class AmazonSESClientMock implements SesClient {
    private SendEmailRequest sendEmailRequest;
    private SendRawEmailRequest sendRawEmailRequest;

    public AmazonSESClientMock() {
    }

    @Override
    public SendEmailResponse sendEmail(SendEmailRequest sendEmailRequest) {
        this.sendEmailRequest = sendEmailRequest;
        return SendEmailResponse.builder().messageId("1").build();
    }

    @Override
    public SendRawEmailResponse sendRawEmail(SendRawEmailRequest sendRawEmailRequest) {
        this.sendRawEmailRequest = sendRawEmailRequest;
        return SendRawEmailResponse.builder().messageId("1").build();
    }

    public SendEmailRequest getSendEmailRequest() {
        return sendEmailRequest;
    }

    public SendRawEmailRequest getSendRawEmailRequest() {
        return sendRawEmailRequest;
    }

    @Override
    public String serviceName() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void close() {
        // TODO Auto-generated method stub

    }
}
