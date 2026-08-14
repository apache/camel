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
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.alibaba.common.models.ServiceKeys;
import org.apache.camel.component.alibaba.sms.constants.SMSHeaders;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

/**
 * Send SMS messages using Alibaba Cloud Short Message Service (SMS).
 */
@UriEndpoint(firstVersion = "4.23.0", scheme = "alibaba-sms", title = "Alibaba Short Message Service (SMS)",
             syntax = "alibaba-sms:operation", category = { Category.CLOUD, Category.MESSAGING },
             headersClass = SMSHeaders.class, producerOnly = true)
public class SMSEndpoint extends DefaultEndpoint {

    @UriPath(description = "Operation to perform", displayName = "Operation", label = "producer",
             enums = "sendSms")
    @Metadata(required = true)
    private String operation;

    @UriParam(description = "Alibaba Cloud region", displayName = "Region")
    @Metadata(required = true)
    private String region;

    @UriParam(description = "SMS endpoint URL. Carries higher precedence than region based client initialization",
              displayName = "Endpoint")
    private String endpoint;

    @UriParam(description = "Access key for the cloud user", displayName = "Access Key",
              secret = true, security = "secret", label = "security")
    private String accessKey;

    @UriParam(description = "Secret key for the cloud user", displayName = "Secret Key",
              secret = true, security = "secret", label = "security")
    private String secretKey;

    @UriParam(description = "Configuration object for cloud service authentication", displayName = "Service Keys",
              secret = true, security = "secret", label = "security")
    private ServiceKeys serviceKeys;

    @UriParam(description = "Phone numbers to send SMS to", displayName = "Phone Numbers")
    private String phoneNumbers;

    @UriParam(description = "SMS sign name", displayName = "Sign Name")
    private String signName;

    @UriParam(description = "SMS template code", displayName = "Template Code")
    private String templateCode;

    @UriParam(description = "SMS template parameters as JSON", displayName = "Template Param")
    private String templateParam;

    @UriParam(description = "Out id for the SMS", displayName = "Out Id")
    private String outId;

    @UriParam(description = "Autowire an existing SMS client instance", displayName = "SMS Client", label = "advanced")
    @Metadata(autowired = true)
    private Client smsClient;

    private boolean autowiredSmsClient;

    public SMSEndpoint() {
    }

    public SMSEndpoint(String uri, String operation, SMSComponent component) {
        super(uri, component);
        this.operation = operation;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new SMSProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("You cannot consume from this endpoint");
    }

    public Client initClient() throws Exception {
        if (smsClient != null) {
            return smsClient;
        }
        smsClient = SMSUtils.createClient(this);
        return smsClient;
    }

    @Override
    protected void doStop() throws Exception {
        if (smsClient != null && !autowiredSmsClient) {
            smsClient = null;
        }
        super.doStop();
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getAccessKey() {
        return accessKey;
    }

    public void setAccessKey(String accessKey) {
        this.accessKey = accessKey;
    }

    public String getSecretKey() {
        return secretKey;
    }

    public void setSecretKey(String secretKey) {
        this.secretKey = secretKey;
    }

    public ServiceKeys getServiceKeys() {
        return serviceKeys;
    }

    public void setServiceKeys(ServiceKeys serviceKeys) {
        this.serviceKeys = serviceKeys;
    }

    public String getPhoneNumbers() {
        return phoneNumbers;
    }

    public void setPhoneNumbers(String phoneNumbers) {
        this.phoneNumbers = phoneNumbers;
    }

    public String getSignName() {
        return signName;
    }

    public void setSignName(String signName) {
        this.signName = signName;
    }

    public String getTemplateCode() {
        return templateCode;
    }

    public void setTemplateCode(String templateCode) {
        this.templateCode = templateCode;
    }

    public String getTemplateParam() {
        return templateParam;
    }

    public void setTemplateParam(String templateParam) {
        this.templateParam = templateParam;
    }

    public String getOutId() {
        return outId;
    }

    public void setOutId(String outId) {
        this.outId = outId;
    }

    public Client getSmsClient() {
        return smsClient;
    }

    public void setSmsClient(Client smsClient) {
        this.smsClient = smsClient;
        this.autowiredSmsClient = smsClient != null;
    }
}
