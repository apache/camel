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
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
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
@BodyTypes(producerIn = @BodyType(value = Carrier.ANY,
                                  byOption = {
                                          @BodyType.When(option = "operation", value = "encrypt",
                                                         carriers = {
                                                                 Carrier.TEXT, Carrier.BYTES, Carrier.STREAM, Carrier.FILE }),
                                          @BodyType.When(option = "operation", value = "decrypt", carriers = Carrier.TEXT) },
                                  description = "encrypt encrypts the body unless the plaintext option or header is set: text"
                                                + " in UTF-8, bytes as they are, and a stream or a file as its bytes, so binary"
                                                + " data is kept. A Map or a POJO fails, as it does not convert to bytes, so"
                                                + " marshal it first. decrypt takes the ciphertext blob as text, unless the"
                                                + " ciphertextBlob option or header is set. No body fails both; generateDataKey"
                                                + " does not use the body"),
           producerOut = @BodyType(value = Carrier.MAP, javaType = "java.util.Map",
                                   description = "A Map with the statusCode and the fields of the response: the ciphertextBlob"
                                                 + " and keyId of encrypt, the plaintext of decrypt, which is Base64 when this"
                                                 + " component encrypted it, and the plaintext and ciphertextBlob of"
                                                 + " generateDataKey. The request id is in the CamelAlibabaKmsRequestId header"
                                                 + " too"))
public class KMSEndpoint extends DefaultEndpoint {

    @UriPath(description = "The operation that the producer performs. The CamelAlibabaKmsOperation header replaces this option"
                           + " for a request that the producer sends. If that header is not set, an exchange property of the"
                           + " same name replaces this option.",
             displayName = "Operation", label = "producer", enums = "encrypt,decrypt,generateDataKey")
    @Metadata(required = true)
    private String operation;

    @UriParam(description = "The region ID of KMS, for example cn-hangzhou. Only used when the endpoint option is not set, to"
                            + " build the endpoint of KMS. One of the two is required, unless the kmsClient option is set or"
                            + " autowired, which uses neither.",
              displayName = "Region")
    private String region;

    @UriParam(description = "The endpoint of KMS, which is a host name without http:// or https://. The producer sends requests"
                            + " to it over HTTPS. If not set, the region option determines the endpoint, and one of the two is"
                            + " required. Not used when the kmsClient option is set or autowired.",
              displayName = "Endpoint")
    private String endpoint;

    @UriParam(description = "The AccessKey ID of the AccessKey pair for the requests that the producer sends to KMS. If not"
                            + " set, the accessKey of the serviceKeys option is used, and one of the two is required. Not used"
                            + " when the kmsClient option is set or autowired.",
              displayName = "Access Key", security = "secret", label = "security")
    private String accessKey;

    @UriParam(description = "The AccessKey secret of the AccessKey pair for the requests that the producer sends to KMS. If not"
                            + " set, the secretKey of the serviceKeys option is used, and one of the two is required. Not used"
                            + " when the kmsClient option is set or autowired.",
              displayName = "Secret Key", security = "secret", label = "security")
    private String secretKey;

    @UriParam(description = "The AccessKey pair as an org.apache.camel.component.alibaba.common.models.ServiceKeys. Its"
                            + " accessKey is the AccessKey ID and its secretKey is the AccessKey secret. Each of the two is"
                            + " used when the option of the same name is not set. Not used when the kmsClient option is set or"
                            + " autowired.",
              displayName = "Service Keys", security = "secret", label = "security")
    private ServiceKeys serviceKeys;

    @UriParam(description = "The ID, alias or ARN of the KMS key for encrypt and generateDataKey. The CamelAlibabaKmsKeyId"
                            + " header replaces this option for a request that the producer sends. If that header is not set,"
                            + " an exchange property of the same name replaces this option. One of the three is required for"
                            + " encrypt and generateDataKey.",
              displayName = "Key Id")
    private String keyId;

    @UriParam(description = "The plaintext for encrypt. The producer encodes it in UTF-8 and sends it to KMS in Base64. The"
                            + " CamelAlibabaKmsPlaintext header replaces this option for a request that the producer sends. If"
                            + " that header is not set, an exchange property of the same name replaces this option. If none of"
                            + " the three is set, the producer takes the plaintext from the message body.",
              displayName = "Plaintext", security = "secret", label = "security")
    private String plaintext;

    @UriParam(description = "The ciphertext blob for decrypt, for example from the response to encrypt or generateDataKey. The"
                            + " CamelAlibabaKmsCiphertextBlob header replaces this option for a request that the producer"
                            + " sends. If that header is not set, an exchange property of the same name replaces this option."
                            + " If none of the three is set, the producer takes the ciphertext blob from the message body as"
                            + " text.",
              displayName = "Ciphertext Blob", security = "secret", label = "security")
    private String ciphertextBlob;

    @UriParam(description = "The key spec of the data key for generateDataKey. The value must be AES_256 or AES_128. KMS does"
                            + " not use it when the number of bytes is set as well. The CamelAlibabaKmsKeySpec header replaces"
                            + " this option for a request that the producer sends. If that header is not set, an exchange"
                            + " property of the same name replaces this option. If none of the three is set, the producer adds"
                            + " no key spec to the request.",
              displayName = "Key Spec", enums = "AES_256,AES_128")
    private String keySpec;

    @UriParam(description = "The number of bytes of the data key for generateDataKey. The value must be from 1 to 1024. The"
                            + " CamelAlibabaKmsNumberOfBytes header replaces this option for a request that the producer sends."
                            + " If that header is not set, an exchange property of the same name replaces this option. If none"
                            + " of the three is set, the producer adds no number of bytes to the request.",
              displayName = "Number Of Bytes")
    private Integer numberOfBytes;

    @UriParam(description = "To use a custom com.aliyun.kms20160120.Client. The producer sends its requests with this client,"
                            + " and the region, endpoint, accessKey, secretKey and serviceKeys options are not used. If no"
                            + " client is set or autowired, the Camel endpoint creates a client from these options. It creates"
                            + " this client when the producer processes its first message.",
              displayName = "KMS Client", label = "advanced")
    @Metadata(autowired = true)
    private volatile Client kmsClient;

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
        Client answer = kmsClient;
        if (answer == null) {
            lock.lock();
            try {
                answer = kmsClient;
                if (answer == null) {
                    answer = createClient();
                    kmsClient = answer;
                }
            } finally {
                lock.unlock();
            }
        }
        return answer;
    }

    Client createClient() throws Exception {
        return KMSUtils.createClient(this);
    }

    @Override
    protected void doStop() throws Exception {
        lock.lock();
        try {
            if (kmsClient != null && !autowiredKmsClient) {
                kmsClient = null;
            }
        } finally {
            lock.unlock();
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
        lock.lock();
        try {
            this.kmsClient = kmsClient;
            this.autowiredKmsClient = kmsClient != null;
        } finally {
            lock.unlock();
        }
    }
}
