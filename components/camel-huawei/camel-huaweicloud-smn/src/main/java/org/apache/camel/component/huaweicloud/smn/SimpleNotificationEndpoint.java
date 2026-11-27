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
package org.apache.camel.component.huaweicloud.smn;

import com.huaweicloud.sdk.smn.v2.SmnClient;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.huaweicloud.common.models.ServiceKeys;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

/**
 * Publish messages to topics of Huawei Cloud Simple Message Notification (SMN).
 */
@UriEndpoint(firstVersion = "3.8.0", scheme = "hwcloud-smn", title = "Huawei Simple Message Notification (SMN)",
             syntax = "hwcloud-smn:smnService",
             category = { Category.CLOUD, Category.MESSAGING }, producerOnly = true)
@BodyTypes(producerIn = @BodyType(value = {
        Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE, Carrier.MAP, Carrier.LIST, Carrier.POJO },
                                  byOption = @BodyType.When(option = "operation", value = "publishAsTemplatedMessage",
                                                            carriers = {
                                                                    Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE,
                                                                    Carrier.MAP, Carrier.LIST, Carrier.POJO, Carrier.NULL }),
                                  description = "The message to publish, as text: bytes, a stream or a file are read as text,"
                                                + " an XML Source or a DOM node as its XML text, and a Map, a List or a POJO is"
                                                + " published as JSON. No body fails for publishAsTextMessage, while"
                                                + " publishAsTemplatedMessage can have no body, with the template from the"
                                                + " CamelHwCloudSmnTemplateName exchange property. The topic comes from the"
                                                + " CamelHwCloudSmnTopic exchange property"),
           producerOut = @BodyType(value = Carrier.UNCHANGED,
                                   description = "Unchanged, with the ids of the message and of the request in the"
                                                 + " CamelHwCloudSmnMessageId and CamelHwCloudSmnRequestId exchange properties. The"
                                                 + " message id is also in the deprecated CamelHwCloudSmnMesssageId exchange"
                                                 + " property"))
public class SimpleNotificationEndpoint extends DefaultEndpoint {

    @UriPath(description = "The SMN service that the producer calls. Only publishMessageService is supported.",
             displayName = "Service name", label = "producer")
    @Metadata(required = true)
    private String smnService;

    @UriParam(description = "The operation that the producer performs: publishAsTextMessage or publishAsTemplatedMessage. The"
                            + " CamelHwCloudSmnOperation exchange property replaces this option.",
              displayName = "Operation name", label = "producer")
    @Metadata(required = true)
    private String operation;

    @UriParam(description = "The AK/SK pair as an org.apache.camel.component.huaweicloud.common.models.ServiceKeys. Its"
                            + " accessKey and its secretKey are each used when the option of the same name is not set.",
              displayName = "Service Configuration",
              security = "secret")
    @Metadata(required = false)
    private ServiceKeys serviceKeys;

    @UriParam(description = "The access key ID (AK) of the Huawei Cloud credentials. If not set, the accessKey of the"
                            + " serviceKeys option is used.",
              displayName = "API access key (AK)", security = "secret")
    @Metadata(required = true)
    private String accessKey;

    @UriParam(description = "The secret access key (SK) of the Huawei Cloud credentials. If not set, the secretKey of the"
                            + " serviceKeys option is used.",
              displayName = "API secret key (SK)", security = "secret")
    @Metadata(required = true)
    private String secretKey;

    @UriParam(description = "The ID of the Huawei Cloud project of the topic. The producer uses it in the URN of the topic"
                            + " and in the credentials of the requests.",
              displayName = "Project ID")
    @Metadata(required = true)
    private String projectId;

    @UriParam(description = "The host name or IP address of the proxy server. The producer uses a proxy only when this option"
                            + " is set.",
              displayName = "Proxy server host")
    @Metadata(required = false)
    private String proxyHost;

    @UriParam(description = "The port of the proxy server. It is used only when the proxyHost option is set.",
              displayName = "Proxy server port")
    @Metadata(required = false)
    private int proxyPort;

    @UriParam(description = "The user name to authenticate with the proxy server. It is used only when the proxyHost option"
                            + " is set.",
              displayName = "Proxy user", security = "secret")
    @Metadata(required = false)
    private String proxyUser;

    @UriParam(description = "The password to authenticate with the proxy server. It is used only when the proxyHost and"
                            + " proxyUser options are set.",
              displayName = "Proxy password", security = "secret")
    @Metadata(required = false)
    private String proxyPassword;

    @UriParam(description = "The region ID of SMN, for example cn-north-4. The producer uses it in the URN of the topic, and to"
                            + " find the endpoint of SMN when the endpoint option is not set.",
              displayName = "Service region")
    @Metadata(required = true)
    private String region;

    @UriParam(description = "The endpoint URL of SMN, for example https://smn.cn-north-4.myhuaweicloud.com. When set, the client"
                            + " uses it instead of the endpoint of the region option. The region option is still needed for"
                            + " the URN of the topic.",
              displayName = "Service endpoint")
    @Metadata(required = false)
    private String endpoint;

    @UriParam(description = "The time to live of the published message in seconds, after which SMN no longer delivers it. SMN"
                            + " accepts at most 86400 (one day). The CamelHwCloudSmnMessageTtl exchange property replaces"
                            + " this option.",
              displayName = "Message TTL", defaultValue = "3600")
    @Metadata(required = false)
    private int messageTtl = 3600;

    @UriParam(description = "Whether to skip the verification of the TLS certificate of the server. This makes the connection"
                            + " insecure: use it only for testing.",
              displayName = "SSL Verification Ignored", defaultValue = "false",
              label = "security", security = "insecure:ssl")
    @Metadata(required = false)
    private boolean ignoreSslVerification;

    private SmnClient smnClient;

    public SimpleNotificationEndpoint() {
    }

    public SimpleNotificationEndpoint(String uri, String smnService, SimpleNotificationComponent component) {
        super(uri, component);
        this.smnService = smnService;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new SimpleNotificationProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("You cannot receive messages from this endpoint");
    }

    public String getSmnService() {
        return smnService;
    }

    public void setSmnService(String smnService) {
        this.smnService = smnService;
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public ServiceKeys getCredentials() {
        return serviceKeys;
    }

    public void setCredentials(ServiceKeys serviceKeys) {
        this.serviceKeys = serviceKeys;
    }

    public ServiceKeys getServiceKeys() {
        return serviceKeys;
    }

    public void setServiceKeys(ServiceKeys serviceKeys) {
        this.serviceKeys = serviceKeys;
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

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getProxyUser() {
        return proxyUser;
    }

    public void setProxyUser(String proxyUser) {
        this.proxyUser = proxyUser;
    }

    public String getProxyPassword() {
        return proxyPassword;
    }

    public void setProxyPassword(String proxyPassword) {
        this.proxyPassword = proxyPassword;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public int getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(int proxyPort) {
        this.proxyPort = proxyPort;
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

    public int getMessageTtl() {
        return messageTtl;
    }

    public void setMessageTtl(int messageTtl) {
        this.messageTtl = messageTtl;
    }

    public boolean isIgnoreSslVerification() {
        return ignoreSslVerification;
    }

    public void setIgnoreSslVerification(boolean ignoreSslVerification) {
        this.ignoreSslVerification = ignoreSslVerification;
    }

    public SmnClient getSmnClient() {
        return smnClient;
    }

    public void setSmnClient(SmnClient smnClient) {
        this.smnClient = smnClient;
    }

}
