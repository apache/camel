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
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
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
@BodyTypes(producerIn = @BodyType(value = Carrier.ANY,
                                  description = "Not used: sendSms takes the phone numbers, sign name, template code and"
                                                + " template parameters from options or headers, such as"
                                                + " CamelAlibabaSmsTemplateParam"),
           producerOut = @BodyType(value = Carrier.MAP,
                                   description = "A Map of the response: the statusCode, and the code, message, bizId and"
                                                 + " requestId the service returns"))
public class SMSEndpoint extends DefaultEndpoint {

    @UriPath(description = "The operation that the producer performs. The CamelAlibabaSmsOperation header replaces this option"
                           + " for a request that the producer sends. If that header is not set, an exchange property of the"
                           + " same name replaces this option.",
             displayName = "Operation", label = "producer", enums = "sendSms")
    @Metadata(required = true)
    private String operation;

    @UriParam(description = "The region ID of Short Message Service, for example cn-hangzhou. Only used when the endpoint"
                            + " option is not set, to determine the endpoint of Short Message Service. Not used when the"
                            + " smsClient option is set or autowired.",
              displayName = "Region")
    @Metadata(required = true)
    private String region;

    @UriParam(description = "The endpoint of Short Message Service, which is a host name without http:// or https://. The"
                            + " producer sends requests to it over HTTPS. If not set, the region option determines the"
                            + " endpoint, and one of the two is required. Not used when the smsClient option is set or"
                            + " autowired.",
              displayName = "Endpoint")
    private String endpoint;

    @UriParam(description = "The AccessKey ID of the AccessKey pair for the requests that the producer sends to Short Message"
                            + " Service. If not set, the accessKey of the serviceKeys option is used, and one of the two is"
                            + " required. Not used when the smsClient option is set or autowired.",
              displayName = "Access Key", security = "secret", label = "security")
    private String accessKey;

    @UriParam(description = "The AccessKey secret of the AccessKey pair for the requests that the producer sends to Short"
                            + " Message Service. If not set, the secretKey of the serviceKeys option is used, and one of the"
                            + " two is required. Not used when the smsClient option is set or autowired.",
              displayName = "Secret Key", security = "secret", label = "security")
    private String secretKey;

    @UriParam(description = "The AccessKey pair as an org.apache.camel.component.alibaba.common.models.ServiceKeys. Its"
                            + " accessKey is the AccessKey ID and its secretKey is the AccessKey secret. Each of the two is"
                            + " used when the option of the same name is not set. Not used when the smsClient option is set or"
                            + " autowired.",
              displayName = "Service Keys", security = "secret", label = "security")
    private ServiceKeys serviceKeys;

    @UriParam(description = "The phone numbers of the recipients, which are separated by commas. The value must have at most"
                            + " 1000 phone numbers. The CamelAlibabaSmsPhoneNumbers header replaces this option for a request"
                            + " that the producer sends. If that header is not set, an exchange property of the same name"
                            + " replaces this option. One of the three is required.",
              displayName = "Phone Numbers")
    private String phoneNumbers;

    @UriParam(description = "The sign name of the SMS message. The value must be an approved sign name. The"
                            + " CamelAlibabaSmsSignName header replaces this option for a request that the producer sends. If"
                            + " that header is not set, an exchange property of the same name replaces this option. One of the"
                            + " three is required.",
              displayName = "Sign Name")
    private String signName;

    @UriParam(description = "The template code of the SMS message. The value must be the code of an approved template. The"
                            + " CamelAlibabaSmsTemplateCode header replaces this option for a request that the producer sends."
                            + " If that header is not set, an exchange property of the same name replaces this option. One of"
                            + " the three is required.",
              displayName = "Template Code")
    private String templateCode;

    @UriParam(description = "The template parameters of the SMS message as a JSON string. The JSON string must provide a value"
                            + " for each variable of the template. The CamelAlibabaSmsTemplateParam header replaces this option"
                            + " for a request that the producer sends. If that header is not set, an exchange property of the"
                            + " same name replaces this option. If none of the three is set, the producer adds no template"
                            + " parameters to the request.",
              displayName = "Template Param")
    private String templateParam;

    @UriParam(description = "The external transaction ID of the SMS message. The CamelAlibabaSmsOutId header replaces this"
                            + " option for a request that the producer sends. If that header is not set, an exchange property"
                            + " of the same name replaces this option. If none of the three is set, the producer adds no"
                            + " external transaction ID to the request.",
              displayName = "Out Id")
    private String outId;

    @UriParam(description = "To use a custom com.aliyun.dysmsapi20170525.Client. The producer sends its requests with this"
                            + " client, and the region, endpoint, accessKey, secretKey and serviceKeys options are not used. If"
                            + " no client is set or autowired, the Camel endpoint creates a client from these options. It"
                            + " creates this client when the producer processes its first message.",
              displayName = "SMS Client", label = "advanced")
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
