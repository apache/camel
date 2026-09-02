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
import com.aliyun.kms20160120.models.DecryptRequest;
import com.aliyun.kms20160120.models.EncryptRequest;
import com.aliyun.kms20160120.models.GenerateDataKeyRequest;
import org.apache.camel.Exchange;
import org.apache.camel.component.alibaba.kms.constants.KMSHeaders;
import org.apache.camel.component.alibaba.kms.constants.KMSOperations;
import org.apache.camel.component.alibaba.kms.models.ClientConfigurations;
import org.apache.camel.support.DefaultProducer;
import org.apache.camel.util.ObjectHelper;

public class KMSProducer extends DefaultProducer {

    private Client kmsClient;

    public KMSProducer(KMSEndpoint endpoint) {
        super(endpoint);
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        KMSEndpoint endpoint = getEndpoint();
        ClientConfigurations configuration = KMSUtils.createClientConfigurations(endpoint, exchange);

        if (ObjectHelper.isEmpty(configuration.operation())) {
            throw new IllegalArgumentException("Operation name not found");
        }

        if (kmsClient == null) {
            kmsClient = endpoint.initClient();
        }

        switch (configuration.operation()) {
            case KMSOperations.ENCRYPT -> encrypt(exchange, configuration);
            case KMSOperations.DECRYPT -> decrypt(exchange, configuration);
            case KMSOperations.GENERATE_DATA_KEY -> generateDataKey(exchange, configuration);
            default -> throw new UnsupportedOperationException("Unsupported operation: " + configuration.operation());
        }
    }

    private void encrypt(Exchange exchange, ClientConfigurations configuration) throws Exception {
        if (ObjectHelper.isEmpty(configuration.keyId())) {
            throw new IllegalArgumentException("Key id is required for encrypt");
        }

        String plaintext = KMSUtils.encodePlaintextForEncrypt(exchange, configuration);
        if (ObjectHelper.isEmpty(plaintext)) {
            throw new IllegalArgumentException("Plaintext is required for encrypt");
        }

        var response = kmsClient.encrypt(new EncryptRequest()
                .setKeyId(configuration.keyId())
                .setPlaintext(plaintext));

        exchange.getMessage().setBody(KMSUtils.toEncryptMap(response));
        setRequestId(exchange, response.getBody() != null ? response.getBody().getRequestId() : null);
    }

    private void decrypt(Exchange exchange, ClientConfigurations configuration) throws Exception {
        String ciphertextBlob = configuration.ciphertextBlob();
        if (ObjectHelper.isEmpty(ciphertextBlob)) {
            ciphertextBlob = exchange.getMessage().getBody(String.class);
        }
        if (ObjectHelper.isEmpty(ciphertextBlob)) {
            throw new IllegalArgumentException("Ciphertext blob is required for decrypt");
        }

        var response = kmsClient.decrypt(new DecryptRequest().setCiphertextBlob(ciphertextBlob));
        exchange.getMessage().setBody(KMSUtils.toDecryptMap(response));
        setRequestId(exchange, response.getBody() != null ? response.getBody().getRequestId() : null);
    }

    private void generateDataKey(Exchange exchange, ClientConfigurations configuration) throws Exception {
        if (ObjectHelper.isEmpty(configuration.keyId())) {
            throw new IllegalArgumentException("Key id is required for generateDataKey");
        }

        GenerateDataKeyRequest request = new GenerateDataKeyRequest().setKeyId(configuration.keyId());
        if (ObjectHelper.isNotEmpty(configuration.keySpec())) {
            request.setKeySpec(configuration.keySpec());
        }
        if (configuration.numberOfBytes() != null) {
            request.setNumberOfBytes(configuration.numberOfBytes());
        }

        var response = kmsClient.generateDataKey(request);
        exchange.getMessage().setBody(KMSUtils.toGenerateDataKeyMap(response));
        setRequestId(exchange, response.getBody() != null ? response.getBody().getRequestId() : null);
    }

    private void setRequestId(Exchange exchange, String requestId) {
        if (ObjectHelper.isNotEmpty(requestId)) {
            exchange.getMessage().setHeader(KMSHeaders.REQUEST_ID, requestId);
        }
    }

    @Override
    public KMSEndpoint getEndpoint() {
        return (KMSEndpoint) super.getEndpoint();
    }
}
