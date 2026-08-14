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
package org.apache.camel.component.alibaba.kms;

import com.aliyun.kms20160120.Client;
import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.alibaba.common.models.ServiceKeys;
import org.apache.camel.component.alibaba.kms.constants.KMSHeaders;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;

/**
 * Encrypt and decrypt data using Alibaba Cloud Key Management Service (KMS).
 */
@UriEndpoint(firstVersion = "4.23.0", scheme = "alibaba-kms", title = "Alibaba Key Management Service (KMS)",
             syntax = "alibaba-kms:operation", category = { Category.CLOUD },
             headersClass = KMSHeaders.class, producerOnly = true)
public class KMSEndpoint extends DefaultEndpoint {

    @UriPath(description = "Operation to perform", displayName = "Operation", label = "producer",
             enums = "encrypt,decrypt,generateDataKey")
    @Metadata(required = true)
    private String operation;

    @UriParam(description = "Alibaba Cloud region", displayName = "Region")
    @Metadata(required = true)
    private String region;

    @UriParam(description = "KMS endpoint URL. Carries higher precedence than region based client initialization",
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

    @UriParam(description = "KMS key id", displayName = "Key Id")
    private String keyId;

    @UriParam(description = "Plaintext to encrypt", displayName = "Plaintext",
              secret = true, security = "secret", label = "security")
    private String plaintext;

    @UriParam(description = "Ciphertext blob to decrypt", displayName = "Ciphertext Blob",
              secret = true, security = "secret", label = "security")
    private String ciphertextBlob;

    @UriParam(description = "Key spec for generateDataKey", displayName = "Key Spec")
    private String keySpec;

    @UriParam(description = "Number of bytes for generateDataKey", displayName = "Number Of Bytes")
    private Integer numberOfBytes;

    @UriParam(description = "Autowire an existing KMS client instance", displayName = "KMS Client", label = "advanced")
    @Metadata(autowired = true)
    private Client kmsClient;

    private boolean autowiredKmsClient;

    public KMSEndpoint() {
    }

    public KMSEndpoint(String uri, String operation, KMSComponent component) {
        super(uri, component);
        this.operation = operation;
    }

    @Override
    public Producer createProducer() throws Exception {
        return new KMSProducer(this);
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("You cannot consume from this endpoint");
    }

    public Client initClient() throws Exception {
        if (kmsClient != null) {
            return kmsClient;
        }
        kmsClient = KMSUtils.createClient(this);
        return kmsClient;
    }

    @Override
    protected void doStop() throws Exception {
        if (kmsClient != null && !autowiredKmsClient) {
            kmsClient = null;
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

    public String getKeyId() {
        return keyId;
    }

    public void setKeyId(String keyId) {
        this.keyId = keyId;
    }

    public String getPlaintext() {
        return plaintext;
    }

    public void setPlaintext(String plaintext) {
        this.plaintext = plaintext;
    }

    public String getCiphertextBlob() {
        return ciphertextBlob;
    }

    public void setCiphertextBlob(String ciphertextBlob) {
        this.ciphertextBlob = ciphertextBlob;
    }

    public String getKeySpec() {
        return keySpec;
    }

    public void setKeySpec(String keySpec) {
        this.keySpec = keySpec;
    }

    public Integer getNumberOfBytes() {
        return numberOfBytes;
    }

    public void setNumberOfBytes(Integer numberOfBytes) {
        this.numberOfBytes = numberOfBytes;
    }

    public Client getKmsClient() {
        return kmsClient;
    }

    public void setKmsClient(Client kmsClient) {
        this.kmsClient = kmsClient;
        this.autowiredKmsClient = kmsClient != null;
    }
}
