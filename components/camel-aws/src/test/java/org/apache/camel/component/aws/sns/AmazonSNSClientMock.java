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
package org.apache.camel.component.aws.sns;

import java.util.ArrayList;
import java.util.List;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.AmazonWebServiceRequest;
import com.amazonaws.ResponseMetadata;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.services.sns.AmazonSNSClient;
import com.amazonaws.services.sns.model.AddPermissionRequest;
import com.amazonaws.services.sns.model.AddPermissionResult;
import com.amazonaws.services.sns.model.ConfirmSubscriptionRequest;
import com.amazonaws.services.sns.model.ConfirmSubscriptionResult;
import com.amazonaws.services.sns.model.CreateTopicRequest;
import com.amazonaws.services.sns.model.CreateTopicResult;
import com.amazonaws.services.sns.model.DeleteTopicRequest;
import com.amazonaws.services.sns.model.DeleteTopicResult;
import com.amazonaws.services.sns.model.GetTopicAttributesRequest;
import com.amazonaws.services.sns.model.GetTopicAttributesResult;
import com.amazonaws.services.sns.model.ListSubscriptionsByTopicRequest;
import com.amazonaws.services.sns.model.ListSubscriptionsByTopicResult;
import com.amazonaws.services.sns.model.ListSubscriptionsRequest;
import com.amazonaws.services.sns.model.ListSubscriptionsResult;
import com.amazonaws.services.sns.model.ListTopicsRequest;
import com.amazonaws.services.sns.model.ListTopicsResult;
import com.amazonaws.services.sns.model.PublishRequest;
import com.amazonaws.services.sns.model.PublishResult;
import com.amazonaws.services.sns.model.RemovePermissionRequest;
import com.amazonaws.services.sns.model.RemovePermissionResult;
import com.amazonaws.services.sns.model.SetTopicAttributesRequest;
import com.amazonaws.services.sns.model.SetTopicAttributesResult;
import com.amazonaws.services.sns.model.SubscribeRequest;
import com.amazonaws.services.sns.model.SubscribeResult;
import com.amazonaws.services.sns.model.Topic;
import com.amazonaws.services.sns.model.UnsubscribeRequest;
import com.amazonaws.services.sns.model.UnsubscribeResult;

import org.junit.Assert;

public class AmazonSNSClientMock extends AmazonSNSClient {
    
    private static final String DEFAULT_TOPIC_ARN = "arn:aws:sns:us-east-1:541925086079:MyTopic";
    private String endpoint;
    
    public AmazonSNSClientMock() {
        super(new BasicAWSCredentials("myAccessKey", "mySecretKey"));
    }
    
    @Override
    public void setEndpoint(String endpoint) {
        this.endpoint = endpoint;
    }
    
    public String getEndpoint() {
        return endpoint;
    }
    
    @Override
    public ConfirmSubscriptionResult confirmSubscription(ConfirmSubscriptionRequest confirmSubscriptionRequest) throws AmazonServiceException, AmazonClientException {
        throw new UnsupportedOperationException();
    }

    @Override
    public GetTopicAttributesResult getTopicAttributes(GetTopicAttributesRequest getTopicAttributesRequest) throws AmazonServiceException, AmazonClientException {
        throw new UnsupportedOperationException();
    }

    @Override
    public SubscribeResult subscribe(SubscribeRequest subscribeRequest) throws AmazonServiceException, AmazonClientException {
        throw new UnsupportedOperationException();
    }

    @Override
    public SetTopicAttributesResult setTopicAttributes(SetTopicAttributesRequest setTopicAttributesRequest) throws AmazonServiceException, AmazonClientException {
        Assert.assertEquals(DEFAULT_TOPIC_ARN, setTopicAttributesRequest.getTopicArn());
        Assert.assertEquals("Policy", setTopicAttributesRequest.getAttributeName());
        Assert.assertEquals("XXX", setTopicAttributesRequest.getAttributeValue());
        return new SetTopicAttributesResult();
    }

    @Override
    public DeleteTopicResult deleteTopic(DeleteTopicRequest deleteTopicRequest) throws AmazonServiceException, AmazonClientException {
        throw new UnsupportedOperationException();
    }

    @Override
    public RemovePermissionResult removePermission(RemovePermissionRequest removePermissionRequest) throws AmazonServiceException, AmazonClientException {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListSubscriptionsResult listSubscriptions(ListSubscriptionsRequest listSubscriptionsRequest) throws AmazonServiceException, AmazonClientException {
        throw new UnsupportedOperationException();
    }

    @Override
    public AddPermissionResult addPermission(AddPermissionRequest addPermissionRequest) throws AmazonServiceException, AmazonClientException {
        throw new UnsupportedOperationException();
    }

    @Override
    public CreateTopicResult createTopic(CreateTopicRequest createTopicRequest) throws AmazonServiceException, AmazonClientException {
        CreateTopicResult createTopicResult = new CreateTopicResult();
        createTopicResult.setTopicArn(DEFAULT_TOPIC_ARN);
        return createTopicResult;
    }

    @Override
    public ListTopicsResult listTopics(ListTopicsRequest listTopicsRequest) throws AmazonServiceException, AmazonClientException {
        throw new UnsupportedOperationException();
    }

    @Override
    public UnsubscribeResult unsubscribe(UnsubscribeRequest unsubscribeRequest) throws AmazonServiceException, AmazonClientException {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListSubscriptionsByTopicResult listSubscriptionsByTopic(ListSubscriptionsByTopicRequest listSubscriptionsByTopicRequest) throws AmazonServiceException, AmazonClientException {
        throw new UnsupportedOperationException();
    }

    @Override
    public PublishResult publish(PublishRequest publishRequest) throws AmazonServiceException, AmazonClientException {
        PublishResult publishResult = new PublishResult();
        publishResult.setMessageId("dcc8ce7a-7f18-4385-bedd-b97984b4363c");
        return publishResult;
    }

    @Override
    public ListSubscriptionsResult listSubscriptions() throws AmazonServiceException, AmazonClientException {
        throw new UnsupportedOperationException();
    }

    @Override
    public ListTopicsResult listTopics() throws AmazonServiceException, AmazonClientException {
        throw new UnsupportedOperationException();
    }
    
    @Override
    public ListTopicsResult listTopics(String nextToken) {
        ListTopicsResult res = new ListTopicsResult();
        Topic topic = new Topic();
        topic.setTopicArn(DEFAULT_TOPIC_ARN);
        List<Topic> list = new ArrayList<Topic>();
        list.add(topic);
        res.setTopics(list);
        return res;
    }

    @Override
    public ResponseMetadata getCachedResponseMetadata(AmazonWebServiceRequest request) {
        throw new UnsupportedOperationException();
    }
}