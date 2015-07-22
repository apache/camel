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
package org.apache.camel.component.aws.ses;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.simpleemail.AmazonSimpleEmailServiceClient;
import com.amazonaws.services.simpleemail.model.SendEmailRequest;
import com.amazonaws.services.simpleemail.model.SendEmailResult;
import com.amazonaws.services.simpleemail.model.SendRawEmailRequest;
import com.amazonaws.services.simpleemail.model.SendRawEmailResult;

public class AmazonSESClientMock extends AmazonSimpleEmailServiceClient {
    private SendEmailRequest sendEmailRequest;
    private SendRawEmailRequest sendRawEmailRequest;

    public AmazonSESClientMock() {
        super(new BasicAWSCredentials("myAccessKey", "mySecretKey"));
    }

    @Override
    public SendEmailResult sendEmail(SendEmailRequest sendEmailRequest) throws AmazonServiceException, AmazonClientException {
        this.sendEmailRequest = sendEmailRequest;
        SendEmailResult result = new SendEmailResult();
        result.setMessageId("1");
        
        return result;
    }
    
    @Override
    public SendRawEmailResult sendRawEmail(SendRawEmailRequest sendRawEmailRequest) throws AmazonServiceException, AmazonClientException {
        this.sendRawEmailRequest = sendRawEmailRequest;
        SendRawEmailResult result = new SendRawEmailResult();
        result.setMessageId("1");
        
        return result;
    }

    public SendEmailRequest getSendEmailRequest() {
        return sendEmailRequest;
    }
    
    public SendRawEmailRequest getSendRawEmailRequest() {
        return sendRawEmailRequest;
    }
}
