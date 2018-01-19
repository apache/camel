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
package org.apache.camel.component.aws.mq;

import java.util.ArrayList;
import java.util.List;

import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.ResponseMetadata;
import com.amazonaws.services.mq.AmazonMQ;
import com.amazonaws.services.mq.model.BrokerState;
import com.amazonaws.services.mq.model.BrokerSummary;
import com.amazonaws.services.mq.model.CreateBrokerRequest;
import com.amazonaws.services.mq.model.CreateBrokerResult;
import com.amazonaws.services.mq.model.CreateConfigurationRequest;
import com.amazonaws.services.mq.model.CreateConfigurationResult;
import com.amazonaws.services.mq.model.CreateUserRequest;
import com.amazonaws.services.mq.model.CreateUserResult;
import com.amazonaws.services.mq.model.DeleteBrokerRequest;
import com.amazonaws.services.mq.model.DeleteBrokerResult;
import com.amazonaws.services.mq.model.DeleteUserRequest;
import com.amazonaws.services.mq.model.DeleteUserResult;
import com.amazonaws.services.mq.model.DescribeBrokerRequest;
import com.amazonaws.services.mq.model.DescribeBrokerResult;
import com.amazonaws.services.mq.model.DescribeConfigurationRequest;
import com.amazonaws.services.mq.model.DescribeConfigurationResult;
import com.amazonaws.services.mq.model.DescribeConfigurationRevisionRequest;
import com.amazonaws.services.mq.model.DescribeConfigurationRevisionResult;
import com.amazonaws.services.mq.model.DescribeUserRequest;
import com.amazonaws.services.mq.model.DescribeUserResult;
import com.amazonaws.services.mq.model.ListBrokersRequest;
import com.amazonaws.services.mq.model.ListBrokersResult;
import com.amazonaws.services.mq.model.ListConfigurationRevisionsRequest;
import com.amazonaws.services.mq.model.ListConfigurationRevisionsResult;
import com.amazonaws.services.mq.model.ListConfigurationsRequest;
import com.amazonaws.services.mq.model.ListConfigurationsResult;
import com.amazonaws.services.mq.model.ListUsersRequest;
import com.amazonaws.services.mq.model.ListUsersResult;
import com.amazonaws.services.mq.model.RebootBrokerRequest;
import com.amazonaws.services.mq.model.RebootBrokerResult;
import com.amazonaws.services.mq.model.UpdateBrokerRequest;
import com.amazonaws.services.mq.model.UpdateBrokerResult;
import com.amazonaws.services.mq.model.UpdateConfigurationRequest;
import com.amazonaws.services.mq.model.UpdateConfigurationResult;
import com.amazonaws.services.mq.model.UpdateUserRequest;
import com.amazonaws.services.mq.model.UpdateUserResult;

public class AmazonMQClientMock implements AmazonMQ {

    public AmazonMQClientMock() {
        super();
    }

    @Override
    public CreateBrokerResult createBroker(CreateBrokerRequest createBrokerRequest) {
        CreateBrokerResult result = new CreateBrokerResult();
        result.setBrokerArn("test");
        result.setBrokerId("1");
        return result;
    }

    @Override
    public CreateConfigurationResult createConfiguration(CreateConfigurationRequest createConfigurationRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public CreateUserResult createUser(CreateUserRequest createUserRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DeleteBrokerResult deleteBroker(DeleteBrokerRequest deleteBrokerRequest) {
        DeleteBrokerResult result = new DeleteBrokerResult();
        result.setBrokerId("1");
        return result;
    }

    @Override
    public DeleteUserResult deleteUser(DeleteUserRequest deleteUserRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DescribeBrokerResult describeBroker(DescribeBrokerRequest describeBrokerRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DescribeConfigurationResult describeConfiguration(DescribeConfigurationRequest describeConfigurationRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DescribeConfigurationRevisionResult describeConfigurationRevision(DescribeConfigurationRevisionRequest describeConfigurationRevisionRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DescribeUserResult describeUser(DescribeUserRequest describeUserRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListBrokersResult listBrokers(ListBrokersRequest listBrokersRequest) {
        ListBrokersResult result = new ListBrokersResult();
        BrokerSummary bs = new BrokerSummary();
        bs.setBrokerArn("aws:test");
        bs.setBrokerId("1");
        bs.setBrokerName("mybroker");
        bs.setBrokerState(BrokerState.RUNNING.toString());
        List<BrokerSummary> list = new ArrayList<>();
        list.add(bs);
        result.setBrokerSummaries(list);
        return result;
    }

    @Override
    public ListConfigurationRevisionsResult listConfigurationRevisions(ListConfigurationRevisionsRequest listConfigurationRevisionsRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListConfigurationsResult listConfigurations(ListConfigurationsRequest listConfigurationsRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListUsersResult listUsers(ListUsersRequest listUsersRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RebootBrokerResult rebootBroker(RebootBrokerRequest rebootBrokerRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public UpdateBrokerResult updateBroker(UpdateBrokerRequest updateBrokerRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public UpdateConfigurationResult updateConfiguration(UpdateConfigurationRequest updateConfigurationRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public UpdateUserResult updateUser(UpdateUserRequest updateUserRequest) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void shutdown() {
        throw new UnsupportedOperationException();
    }

    @Override
    public ResponseMetadata getCachedResponseMetadata(AmazonWebServiceRequest request) {
        throw new UnsupportedOperationException();
    }

}
