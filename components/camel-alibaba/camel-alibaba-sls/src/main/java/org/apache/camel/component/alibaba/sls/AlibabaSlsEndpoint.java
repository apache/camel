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
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

/**
 * Manage logs on Alibaba Cloud Simple Log Service (SLS).
 */
@UriEndpoint(firstVersion = "4.23.0", scheme = "alibaba-sls", title = "Alibaba Simple Log Service (SLS)",
             syntax = "alibaba-sls:operation", category = { Category.CLOUD, Category.MONITORING },
             headersClass = AlibabaSlsHeaders.class, producerOnly = true)
public class AlibabaSlsEndpoint extends DefaultEndpoint {

    @UriPath(description = "Operation to perform", displayName = "Operation", label = "producer",
             enums = "putLogs,getLogs,listLogStores")
    @Metadata(required = true)
    private String operation;

    @UriParam(description = "Alibaba Cloud region", displayName = "Region")
    private String region;

    @UriParam(description = "SLS endpoint URL (e.g. cn-hangzhou.log.aliyuncs.com). "
                            + "Carries higher precedence than region based client initialization",
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

    @UriParam(description = "SLS project name", displayName = "Project")
    private String project;

    @UriParam(description = "SLS log store name", displayName = "Log Store Name")
    private String logStoreName;

    @UriParam(description = "Log query string for getLogs", displayName = "Query", label = "getLogs")
    private String query;

    @UriParam(description = "Query start time for getLogs (Unix timestamp in seconds)", displayName = "From",
              label = "getLogs")
    private Integer from;

    @UriParam(description = "Query end time for getLogs (Unix timestamp in seconds)", displayName = "To",
              label = "getLogs")
    private Integer to;

    @UriParam(description = "Maximum number of log lines to return for getLogs", displayName = "Line",
              label = "getLogs")
    private Long line;

    @UriParam(description = "Log query offset for getLogs", displayName = "Offset", label = "getLogs")
    private Long offset;

    @UriParam(description = "Log topic filter for getLogs", displayName = "Topic", label = "getLogs")
    private String topic;

    @UriParam(description = "Whether to return logs in reverse order for getLogs", displayName = "Reverse",
              label = "getLogs")
    private Boolean reverse;

    @UriParam(description = "Autowire an existing SLS client instance", displayName = "SLS Client", label = "advanced")
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
