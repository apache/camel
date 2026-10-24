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
package org.apache.camel.component.aws2.kms;

import org.apache.camel.Category;
import org.apache.camel.Component;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.component.aws2.kms.client.KMS2ClientFactory;
import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.spi.EndpointServiceLocation;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.support.ScheduledPollEndpoint;
import org.apache.camel.util.ObjectHelper;
import software.amazon.awssdk.services.kms.KmsClient;

/**
 * Manage keys stored in AWS KMS instances.
 */
@UriEndpoint(firstVersion = "3.1.0", scheme = "aws2-kms", title = "AWS Key Management Service (KMS)",
             syntax = "aws2-kms:label", producerOnly = true, category = { Category.CLOUD, Category.MANAGEMENT },
             headersClass = KMS2Constants.class)
@BodyTypes(producerIn = @BodyType(value = Carrier.ANY,
                                  byOption = @BodyType.When(option = "pojoRequest", value = "true", carriers = Carrier.POJO),
                                  description = "Not used: the key id and the other inputs come from headers, such as"
                                                + " CamelAwsKMSKeyId. With pojoRequest=true the request of the AWS SDK for the"
                                                + " operation, such as a ListKeysRequest: another body fails"),
           producerOut = @BodyType(value = Carrier.POJO,
                                   description = "The response of the AWS SDK for the operation, such as the CreateKeyResponse"
                                                 + " of createKey, which puts the id, the ARN and the state of the key in"
                                                 + " headers too"))
public class KMS2Endpoint extends ScheduledPollEndpoint implements EndpointServiceLocation {

    private KmsClient kmsClient;

    @UriParam
    private KMS2Configuration configuration;

    public KMS2Endpoint(String uri, Component component, KMS2Configuration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }

    @Override
    public Consumer createConsumer(Processor processor) throws Exception {
        throw new UnsupportedOperationException("You cannot receive messages from this endpoint");
    }

    @Override
    public Producer createProducer() throws Exception {
        return new KMS2Producer(this);
    }

    @Override
    public void doStart() throws Exception {
        super.doStart();

        kmsClient = ObjectHelper.isNotEmpty(configuration.getKmsClient())
                ? configuration.getKmsClient()
                : KMS2ClientFactory.getKmsClient(configuration);
    }

    @Override
    public void doStop() throws Exception {
        if (ObjectHelper.isEmpty(configuration.getKmsClient())) {
            if (ObjectHelper.isNotEmpty(kmsClient)) {
                kmsClient.close();
            }
        }
        super.doStop();
    }

    public KMS2Configuration getConfiguration() {
        return configuration;
    }

    public KmsClient getKmsClient() {
        return kmsClient;
    }

    @Override
    public String getServiceUrl() {
        if (!configuration.isOverrideEndpoint()) {
            if (ObjectHelper.isNotEmpty(configuration.getRegion())) {
                return configuration.getRegion();
            }
        } else if (ObjectHelper.isNotEmpty(configuration.getUriEndpointOverride())) {
            return configuration.getUriEndpointOverride();
        }
        return null;
    }

    @Override
    public String getServiceProtocol() {
        return "kms";
    }
}
