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
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
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
@BodyTypes(producerIn = @BodyType(value = Carrier.ANY,
                                  byOption = {
                                          @BodyType.When(option = "operation", value = "putRow", carriers = Carrier.POJO,
                                                         javaType = "com.alicloud.openservices.tablestore.model.PutRowRequest"),
                                          @BodyType.When(option = "operation", value = "getRow", carriers = Carrier.POJO,
                                                         javaType = "com.alicloud.openservices.tablestore.model.GetRowRequest"),
                                          @BodyType.When(option = "operation", value = "updateRow", carriers = Carrier.POJO,
                                                         javaType = "com.alicloud.openservices.tablestore.model.UpdateRowRequest"),
                                          @BodyType.When(option = "operation", value = "deleteRow", carriers = Carrier.POJO,
                                                         javaType = "com.alicloud.openservices.tablestore.model.DeleteRowRequest") },
                                  description = "The request of the Tablestore SDK for the row operations, such as a"
                                                + " PutRowRequest for putRow: any other body fails. listTables does not"
                                                + " read the body"),
           producerOut = @BodyType(value = Carrier.MAP,
                                   byOption = @BodyType.When(option = "operation", value = "listTables",
                                                             carriers = Carrier.LIST,
                                                             javaType = "java.util.List<String>"),
                                   description = "A Map of the response for the row operations: the requestId, the"
                                                 + " consumedCapacity and the row as a Map of its primaryKey and columns."
                                                 + " listTables gives a List of the table names"))
public class AlibabaOtsEndpoint extends DefaultEndpoint {

    @UriPath(description = "The operation that the producer performs. The CamelAlibabaOtsOperation header replaces this option"
                           + " for a request that the producer sends. If that header is not set, an exchange property of the"
                           + " same name replaces this option.",
             displayName = "Operation", label = "producer", enums = "putRow,getRow,updateRow,deleteRow,listTables")
    @Metadata(required = true)
    private String operation;

    @UriParam(description = "The endpoint of the Tablestore instance, which starts with http:// or https://, for example"
                            + " https://my-instance.cn-hangzhou.ots.aliyuncs.com. Not used when the otsClient option is set or"
                            + " autowired.",
              displayName = "Endpoint")
    @Metadata(required = true)
    private String endpoint;

    @UriParam(description = "The name of the Tablestore instance. Not used when the otsClient option is set or autowired.",
              displayName = "Instance Name")
    @Metadata(required = true)
    private String instanceName;

    @UriParam(description = "The AccessKey ID of the AccessKey pair for the requests that the producer sends to Tablestore. If"
                            + " not set, the accessKey of the serviceKeys option is used, and one of the two is required. Not"
                            + " used when the otsClient option is set or autowired.",
              displayName = "Access Key", security = "secret", label = "security")
    private String accessKey;

    @UriParam(description = "The AccessKey secret of the AccessKey pair for the requests that the producer sends to Tablestore."
                            + " If not set, the secretKey of the serviceKeys option is used, and one of the two is required."
                            + " Not used when the otsClient option is set or autowired.",
              displayName = "Secret Key", security = "secret", label = "security")
    private String secretKey;

    @UriParam(description = "The AccessKey pair as an org.apache.camel.component.alibaba.common.models.ServiceKeys, whose"
                            + " accessKey is the AccessKey ID and whose secretKey is the AccessKey secret. Each of the two is"
                            + " used when the option of the same name is not set. Not used when the otsClient option is set or"
                            + " autowired.",
              displayName = "Service Keys", security = "secret", label = "security")
    private ServiceKeys serviceKeys;

    @UriParam(description = "To use a custom com.alicloud.openservices.tablestore.SyncClient. The producer sends its requests"
                            + " with this client, and the endpoint, instanceName, accessKey, secretKey and serviceKeys options"
                            + " are not used. The Camel endpoint does not shut down a client that is set or autowired. If no"
                            + " client is set or autowired, the Camel endpoint creates a SyncClient from these options when the"
                            + " producer processes its first message, and shuts it down when the Camel endpoint stops.",
              displayName = "OTS Client", label = "advanced")
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
