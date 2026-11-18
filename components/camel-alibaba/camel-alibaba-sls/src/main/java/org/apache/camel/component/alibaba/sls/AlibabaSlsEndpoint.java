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
package org.apache.camel.component.alibaba.sls;

import com.aliyun.sls20201230.Client;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.alibaba.common.models.ServiceKeys;
import org.apache.camel.component.alibaba.sls.constants.AlibabaSlsHeaders;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

/**
 * Write logs, query logs, and list Logstores on Alibaba Cloud Simple Log Service (SLS).
 */
@UriEndpoint(firstVersion = "4.23.0", scheme = "alibaba-sls", title = "Alibaba Simple Log Service (SLS)",
             syntax = "alibaba-sls:operation", category = { Category.CLOUD, Category.MONITORING },
             headersClass = AlibabaSlsHeaders.class, producerOnly = true)
@BodyTypes(producerIn = @BodyType(value = Carrier.ANY,
                                  byOption = @BodyType.When(option = "operation", value = "putLogs", carriers = Carrier.POJO),
                                  description = "The logs to write for putLogs: a LogGroup or a PutLogsRequest of the SLS"
                                                + " SDK, any other body fails. getLogs and listLogStores do not read the"
                                                + " body, they take their input from options and headers"),
           producerOut = @BodyType(value = Carrier.MAP,
                                   description = "A Map of the response with its statusCode and headers. For getLogs the"
                                                 + " GetLogsResponseBody of the SLS SDK is under body, for listLogStores"
                                                 + " the names under logstores with count and total"))
public class AlibabaSlsEndpoint extends DefaultEndpoint {

    @UriPath(description = "The operation that the producer performs. The CamelAlibabaSlsOperation header replaces"
                           + " this option for a request that the producer sends. If that header is not set, an"
                           + " exchange property of the same name replaces this option.",
             displayName = "Operation", label = "producer",
             enums = "putLogs,getLogs,listLogStores")
    @Metadata(required = true)
    private String operation;

    @UriParam(description = "The region ID of Simple Log Service, for example cn-hangzhou. Only used when the endpoint"
                            + " option is not set, to build the endpoint of Simple Log Service. Not used when the"
                            + " slsClient option is set or autowired.",
              displayName = "Region")
    private String region;

    @UriParam(description = "The endpoint of Simple Log Service, which is a host name without http:// or https://, for"
                            + " example cn-hangzhou.log.aliyuncs.com. The producer sends requests over HTTPS to the"
                            + " endpoint, with the name of the project in front of it. If not set, the region option"
                            + " determines the endpoint, and one of the two is required. Not used when the slsClient"
                            + " option is set or autowired.",
              displayName = "Endpoint")
    private String endpoint;

    @UriParam(description = "The AccessKey ID of the AccessKey pair for the requests that the producer sends to Simple"
                            + " Log Service. If not set, the accessKey of the serviceKeys option is used, and one of"
                            + " the two is required. Not used when the slsClient option is set or autowired.",
              displayName = "Access Key", security = "secret",
              label = "security")
    private String accessKey;

    @UriParam(description = "The AccessKey secret of the AccessKey pair for the requests that the producer sends to"
                            + " Simple Log Service. If not set, the secretKey of the serviceKeys option is used, and"
                            + " one of the two is required. Not used when the slsClient option is set or autowired.",
              displayName = "Secret Key", security = "secret",
              label = "security")
    private String secretKey;

    @UriParam(description = "The AccessKey pair as an org.apache.camel.component.alibaba.common.models.ServiceKeys."
                            + " Its accessKey is the AccessKey ID and its secretKey is the AccessKey secret. Each of"
                            + " the two is used when the option of the same name is not set. Not used when the"
                            + " slsClient option is set or autowired.",
              displayName = "Service Keys",
              security = "secret", label = "security")
    private ServiceKeys serviceKeys;

    @UriParam(description = "The name of the project. The CamelAlibabaSlsProject header replaces this option for a"
                            + " request that the producer sends. If that header is not set, an exchange property of"
                            + " the same name replaces this option. One of the three is required.",
              displayName = "Project")
    private String project;

    @UriParam(description = "The name of the Logstore for putLogs and getLogs. The producer writes the logs to this"
                            + " Logstore for putLogs, and queries its logs for getLogs. The"
                            + " CamelAlibabaSlsLogStoreName header replaces this option for a request that the"
                            + " producer sends. If that header is not set, an exchange property of the same name"
                            + " replaces this option. One of the three is required for putLogs and getLogs.",
              displayName = "Log Store Name")
    private String logStoreName;

    @UriParam(description = "The search statement or analytic statement for getLogs. The CamelAlibabaSlsQuery header"
                            + " replaces this option for a request that the producer sends. If that header is not set,"
                            + " an exchange property of the same name replaces this option. If none of the three is"
                            + " set, the producer adds no search statement or analytic statement to the request.",
              displayName = "Query", label = "getLogs")
    private String query;

    @UriParam(description = "The start time for getLogs, as a Unix timestamp in seconds. Simple Log Service requires a"
                            + " start time. The CamelAlibabaSlsFrom header replaces this option for a request that the"
                            + " producer sends. If that header is not set, an exchange property of the same name"
                            + " replaces this option. If none of the three is set, the producer adds no start time to"
                            + " the request.",
              displayName = "From",
              label = "getLogs")
    private Integer from;

    @UriParam(description = "The end time for getLogs, as a Unix timestamp in seconds. Simple Log Service requires an"
                            + " end time. The CamelAlibabaSlsTo header replaces this option for a request that the"
                            + " producer sends. If that header is not set, an exchange property of the same name"
                            + " replaces this option. If none of the three is set, the producer adds no end time to"
                            + " the request.",
              displayName = "To",
              label = "getLogs")
    private Integer to;

    @UriParam(description = "The maximum number of logs to return for getLogs. Simple Log Service accepts a value of"
                            + " at most 100. Simple Log Service does not use it when query contains an analytic"
                            + " statement. The CamelAlibabaSlsLine header replaces this option for a request that the"
                            + " producer sends. If that header is not set, an exchange property of the same name"
                            + " replaces this option. If none of the three is set, the producer adds no maximum number"
                            + " of logs to the request.",
              displayName = "Line",
              label = "getLogs")
    private Long line;

    @UriParam(description = "The offset of the first log to return for getLogs. Simple Log Service does not use it"
                            + " when query contains an analytic statement. The CamelAlibabaSlsOffset header replaces"
                            + " this option for a request that the producer sends. If that header is not set, an"
                            + " exchange property of the same name replaces this option. If none of the three is set,"
                            + " the producer adds no offset to the request.",
              displayName = "Offset", label = "getLogs")
    private Long offset;

    @UriParam(description = "The log topic for getLogs. The CamelAlibabaSlsTopic header replaces this option for a"
                            + " request that the producer sends. If that header is not set, an exchange property of"
                            + " the same name replaces this option. If none of the three is set, the producer adds no"
                            + " log topic to the request.",
              displayName = "Topic", label = "getLogs")
    private String topic;

    @UriParam(description = "Whether to return logs in descending order of log timestamps for getLogs. Simple Log"
                            + " Service does not use it when query contains an analytic statement. The"
                            + " CamelAlibabaSlsReverse header replaces this option for a request that the producer"
                            + " sends. If that header is not set, an exchange property of the same name replaces this"
                            + " option. If none of the three is set, the producer adds no sort order to the request.",
              displayName = "Reverse",
              label = "getLogs")
    private Boolean reverse;

    @UriParam(description = "To use a custom com.aliyun.sls20201230.Client. The producer sends its requests with this"
                            + " client, and the region, endpoint, accessKey, secretKey and serviceKeys options are not"
                            + " used. If no client is set or autowired, the Camel endpoint creates a client from these"
                            + " options. It creates this client when the producer processes its first message.",
              displayName = "SLS Client", label = "advanced")
    @Metadata(autowired = true)
    private Client slsClient;

    private boolean autowiredSlsClient;

    public AlibabaSlsEndpoint() {
    }

    public AlibabaSlsEndpoint(String uri, String operation, AlibabaSlsComponent component) {
        super(uri, component);
        this.operation = operation;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new AlibabaSlsProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("You cannot consume from this endpoint");
    }

    public Client initClient() throws Exception {
        if (slsClient != null) {
            return slsClient;
        }
        slsClient = AlibabaSlsUtils.createClient(this);
        return slsClient;
    }

    @Override
    protected void doStop() throws Exception {
        if (slsClient != null && !autowiredSlsClient) {
            // com.aliyun.sls20201230.Client extends com.aliyun.teaopenapi.Client, which has no
            // close/shutdown lifecycle API (same as camel-alibaba-eventbridge)
            slsClient = null;
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

    public String getProject() {
        return project;
    }

    public void setProject(String project) {
        this.project = project;
    }

    public String getLogStoreName() {
        return logStoreName;
    }

    public void setLogStoreName(String logStoreName) {
        this.logStoreName = logStoreName;
    }

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public Integer getFrom() {
        return from;
    }

    public void setFrom(Integer from) {
        this.from = from;
    }

    public Integer getTo() {
        return to;
    }

    public void setTo(Integer to) {
        this.to = to;
    }

    public Long getLine() {
        return line;
    }

    public void setLine(Long line) {
        this.line = line;
    }

    public Long getOffset() {
        return offset;
    }

    public void setOffset(Long offset) {
        this.offset = offset;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public Boolean getReverse() {
        return reverse;
    }

    public void setReverse(Boolean reverse) {
        this.reverse = reverse;
    }

    public Client getSlsClient() {
        return slsClient;
    }

    public void setSlsClient(Client slsClient) {
        this.slsClient = slsClient;
        this.autowiredSlsClient = slsClient != null;
    }
}
