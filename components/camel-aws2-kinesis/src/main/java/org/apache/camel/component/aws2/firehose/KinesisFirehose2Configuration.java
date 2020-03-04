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
package org.apache.camel.component.aws2.firehose;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriParams;
import org.apache.camel.spi.UriPath;
import software.amazon.awssdk.core.Protocol;
import software.amazon.awssdk.services.firehose.FirehoseClient;

@UriParams
public class KinesisFirehose2Configuration implements Cloneable {

    @UriPath(description = "Name of the stream")
    @Metadata(required = true)
    private String streamName;
    @UriParam(label = "security", secret = true, description = "Amazon AWS Access Key")
    private String accessKey;
    @UriParam(label = "security", secret = true, description = "Amazon AWS Secret Key")
    private String secretKey;
    @UriParam(description = "The region in which Kinesis Firehose client needs to work. When using this parameter, the configuration will expect the lowercase name of the "
                            + "region (for example ap-east-1) You'll need to use the name Region.EU_WEST_1.id()")
    private String region;
    @UriParam(description = "Amazon Kinesis Firehose client to use for all requests for this endpoint")
    private FirehoseClient amazonKinesisFirehoseClient;
    @UriParam(enums = "HTTP,HTTPS", defaultValue = "HTTPS", description = "To define a proxy protocol when instantiating the Kinesis Firehose client")
    private Protocol proxyProtocol = Protocol.HTTPS;
    @UriParam(description = "To define a proxy host when instantiating the Kinesis Firehose client")
    private String proxyHost;
    @UriParam(description = "To define a proxy port when instantiating the Kinesis Firehose client")
    private Integer proxyPort;

    public void setAmazonKinesisFirehoseClient(FirehoseClient client) {
        this.amazonKinesisFirehoseClient = client;
    }

    public FirehoseClient getAmazonKinesisFirehoseClient() {
        return amazonKinesisFirehoseClient;
    }

    public void setStreamName(String streamName) {
        this.streamName = streamName;
    }

    public String getStreamName() {
        return streamName;
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

    public String getRegion() {
        return region;
    }

    public void setRegion(String region) {
        this.region = region;
    }

    public Protocol getProxyProtocol() {
        return proxyProtocol;
    }

    public void setProxyProtocol(Protocol proxyProtocol) {
        this.proxyProtocol = proxyProtocol;
    }

    public String getProxyHost() {
        return proxyHost;
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost = proxyHost;
    }

    public Integer getProxyPort() {
        return proxyPort;
    }

    public void setProxyPort(Integer proxyPort) {
        this.proxyPort = proxyPort;
    }

    // *************************************************
    //
    // *************************************************

    public KinesisFirehose2Configuration copy() {
        try {
            return (KinesisFirehose2Configuration)super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeCamelException(e);
        }
    }
}
