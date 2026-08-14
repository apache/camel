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
package org.apache.camel.component.alibaba.ots;

import com.alicloud.openservices.tablestore.SyncClient;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.alibaba.common.models.ServiceKeys;
import org.apache.camel.component.alibaba.ots.constants.AlibabaOtsHeaders;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

/**
 * Perform row operations on Alibaba Cloud Tablestore (OTS).
 */
@UriEndpoint(firstVersion = "4.23.0", scheme = "alibaba-ots", title = "Alibaba Tablestore (OTS)",
             syntax = "alibaba-ots:operation", category = { Category.CLOUD, Category.DATABASE },
             headersClass = AlibabaOtsHeaders.class, producerOnly = true)
public class AlibabaOtsEndpoint extends DefaultEndpoint {

    @UriPath(description = "Operation to perform", displayName = "Operation", label = "producer",
             enums = "putRow,getRow,updateRow,deleteRow,listTables")
    @Metadata(required = true)
    private String operation;

    @UriParam(description = "Tablestore endpoint URL", displayName = "Endpoint")
    @Metadata(required = true)
    private String endpoint;

    @UriParam(description = "Tablestore instance name", displayName = "Instance Name")
    @Metadata(required = true)
    private String instanceName;

    @UriParam(description = "Access key for the cloud user", displayName = "Access Key",
              secret = true, security = "secret", label = "security")
    private String accessKey;

    @UriParam(description = "Secret key for the cloud user", displayName = "Secret Key",
              secret = true, security = "secret", label = "security")
    private String secretKey;

    @UriParam(description = "Configuration object for cloud service authentication", displayName = "Service Keys",
              secret = true, security = "secret", label = "security")
    private ServiceKeys serviceKeys;

    @UriParam(description = "Autowire an existing Tablestore client instance", displayName = "OTS Client",
              label = "advanced")
    @Metadata(autowired = true)
    private SyncClient otsClient;

    private boolean autowiredOtsClient;

    public AlibabaOtsEndpoint() {
    }

    public AlibabaOtsEndpoint(String uri, String operation, AlibabaOtsComponent component) {
        super(uri, component);
        this.operation = operation;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new AlibabaOtsProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("You cannot consume from this endpoint");
    }

    public SyncClient initClient() throws Exception {
        if (otsClient != null) {
            return otsClient;
        }
        otsClient = AlibabaOtsUtils.createClient(this);
        return otsClient;
    }

    @Override
    protected void doStop() throws Exception {
        if (otsClient != null && !autowiredOtsClient) {
            otsClient.shutdown();
            otsClient = null;
        }
        super.doStop();
    }

    public String getOperation() {
        return operation;
    }

    public void setOperation(String operation) {
        this.operation = operation;
    }

    public String getEndpoint() {
        return endpoint;
    }

    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }

    public String getInstanceName() {
        return instanceName;
    }

    public void setInstanceName(String instanceName) {
        this.instanceName = instanceName;
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

    public SyncClient getOtsClient() {
        return otsClient;
    }

    public void setOtsClient(SyncClient otsClient) {
        this.otsClient = otsClient;
        this.autowiredOtsClient = otsClient != null;
    }
}
