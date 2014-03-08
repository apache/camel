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
package org.apache.camel.component.aws.cw;

/**
 * The AWS CW component configuration properties
 * 
 */
import java.util.Date;

import com.amazonaws.services.cloudwatch.AmazonCloudWatch;

public class CwConfiguration implements Cloneable {

    private AmazonCloudWatch amazonCwClient;
    private String amazonCwEndpoint;
    private String accessKey;
    private String secretKey;
    private String name;
    private Double value;
    private String unit;
    private String namespace;
    private Date timestamp;

    public void setAmazonCwEndpoint(String amazonCwEndpoint) {
        this.amazonCwEndpoint = amazonCwEndpoint;
    }

    public String getAmazonCwEndpoint() {
        return amazonCwEndpoint;
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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Double getValue() {
        return value;
    }

    public void setValue(Double value) {
        this.value = value;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "CwConfiguration[name=" + name
                + ", amazonCwClient=" + amazonCwClient
                + ", accessKey=" + accessKey
                + ", secretKey=xxxxxxxxxxxxxxx"
                + ", value=" + value
                + ", unit=" + unit
                + "]";
    }

    public AmazonCloudWatch getAmazonCwClient() {
        return amazonCwClient;
    }

    public void setAmazonCwClient(AmazonCloudWatch amazonCwClient) {
        this.amazonCwClient = amazonCwClient;
    }
}