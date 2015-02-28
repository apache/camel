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
package org.apache.camel.component.aws.sdb;

import com.amazonaws.services.simpledb.AmazonSimpleDB;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;

@UriParams
public class SdbConfiguration {

    @UriPath @Metadata(required = "true")
    private String domainName;
    @UriParam
    private AmazonSimpleDB amazonSDBClient;
    @UriParam
    private String accessKey;
    @UriParam
    private String secretKey;
    @UriParam
    private String amazonSdbEndpoint;
    @UriParam
    private Integer maxNumberOfDomains;
    @UriParam(defaultValue = "false")
    private Boolean consistentRead;
    @UriParam(defaultValue = "PutAttributes")
    private SdbOperations operation = SdbOperations.PutAttributes;

    public void setAmazonSdbEndpoint(String amazonSdbEndpoint) {
        this.amazonSdbEndpoint = amazonSdbEndpoint;
    }

    public String getAmazonSdbEndpoint() {
        return amazonSdbEndpoint;
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

    public AmazonSimpleDB getAmazonSDBClient() {
        return amazonSDBClient;
    }

    public void setAmazonSDBClient(AmazonSimpleDB amazonSDBClient) {
        this.amazonSDBClient = amazonSDBClient;
    }

    public String getDomainName() {
        return domainName;
    }

    public void setDomainName(String domainName) {
        this.domainName = domainName;
    }

    public SdbOperations getOperation() {
        return operation;
    }

    public void setOperation(SdbOperations operation) {
        this.operation = operation;
    }

    public Integer getMaxNumberOfDomains() {
        return maxNumberOfDomains;
    }

    public void setMaxNumberOfDomains(Integer maxNumberOfDomains) {
        this.maxNumberOfDomains = maxNumberOfDomains;
    }

    public Boolean getConsistentRead() {
        return consistentRead;
    }

    public void setConsistentRead(Boolean consistentRead) {
        this.consistentRead = consistentRead;
    }
}
