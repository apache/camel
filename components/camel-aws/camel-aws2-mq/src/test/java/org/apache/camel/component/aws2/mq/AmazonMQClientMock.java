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
package org.apache.camel.component.aws2.mq;

import java.util.ArrayList;
import java.util.List;

import software.amazon.awssdk.services.mq.MqClient;
import software.amazon.awssdk.services.mq.MqServiceClientConfiguration;
import software.amazon.awssdk.services.mq.model.BrokerState;
import software.amazon.awssdk.services.mq.model.BrokerSummary;
import software.amazon.awssdk.services.mq.model.ConfigurationId;
import software.amazon.awssdk.services.mq.model.CreateBrokerRequest;
import software.amazon.awssdk.services.mq.model.CreateBrokerResponse;
import software.amazon.awssdk.services.mq.model.DeleteBrokerRequest;
import software.amazon.awssdk.services.mq.model.DeleteBrokerResponse;
import software.amazon.awssdk.services.mq.model.DescribeBrokerRequest;
import software.amazon.awssdk.services.mq.model.DescribeBrokerResponse;
import software.amazon.awssdk.services.mq.model.ListBrokersRequest;
import software.amazon.awssdk.services.mq.model.ListBrokersResponse;
import software.amazon.awssdk.services.mq.model.RebootBrokerRequest;
import software.amazon.awssdk.services.mq.model.RebootBrokerResponse;
import software.amazon.awssdk.services.mq.model.UpdateBrokerRequest;
import software.amazon.awssdk.services.mq.model.UpdateBrokerResponse;

public class AmazonMQClientMock implements MqClient {

    public AmazonMQClientMock() {
    }

    @Override
    public CreateBrokerResponse createBroker(CreateBrokerRequest createBrokerRequest) {
        CreateBrokerResponse.Builder builder = CreateBrokerResponse.builder();
        builder.brokerArn("test").brokerId("1");
        return builder.build();
    }

    @Override
    public DeleteBrokerResponse deleteBroker(DeleteBrokerRequest deleteBrokerRequest) {
        DeleteBrokerResponse.Builder builder = DeleteBrokerResponse.builder();
        builder.brokerId("1");
        return builder.build();
    }

    @Override
    public DescribeBrokerResponse describeBroker(DescribeBrokerRequest describeBrokerRequest) {
        DescribeBrokerResponse.Builder builder = DescribeBrokerResponse.builder();
        builder.brokerId("1").brokerName("Test").brokerState(BrokerState.RUNNING.toString());
        return builder.build();
    }

    @Override
    public ListBrokersResponse listBrokers(ListBrokersRequest listBrokersRequest) {
        ListBrokersResponse.Builder builder = ListBrokersResponse.builder();
        BrokerSummary.Builder bs = BrokerSummary.builder();
        bs.brokerArn("aws:test");
        bs.brokerId("1");
        bs.brokerName("mybroker");
        bs.brokerState(BrokerState.RUNNING.toString());
        List<BrokerSummary> list = new ArrayList<>();
        list.add(bs.build());
        builder.brokerSummaries(list);
        return builder.build();
    }

    @Override
    public RebootBrokerResponse rebootBroker(RebootBrokerRequest rebootBrokerRequest) {
        RebootBrokerResponse.Builder builder = RebootBrokerResponse.builder();
        return builder.build();
    }

    @Override
    public UpdateBrokerResponse updateBroker(UpdateBrokerRequest updateBrokerRequest) {
        UpdateBrokerResponse.Builder builder = UpdateBrokerResponse.builder();
        ConfigurationId.Builder cId = ConfigurationId.builder();
        cId.id("1");
        cId.revision(12);
        builder.brokerId("1").configuration(cId.build());
        return builder.build();
    }

    @Override
    public MqServiceClientConfiguration serviceClientConfiguration() {
        return null;
    }

    @Override
    public String serviceName() {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public void close() {
        // TODO Auto-generated method stub

    }

}
