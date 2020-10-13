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
package org.apache.camel.component.aws2.sns;

import java.util.ArrayList;
import java.util.List;

import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.CreateTopicRequest;
import software.amazon.awssdk.services.sns.model.CreateTopicResponse;
import software.amazon.awssdk.services.sns.model.ListTopicsRequest;
import software.amazon.awssdk.services.sns.model.ListTopicsResponse;
import software.amazon.awssdk.services.sns.model.PublishRequest;
import software.amazon.awssdk.services.sns.model.PublishResponse;
import software.amazon.awssdk.services.sns.model.SetTopicAttributesRequest;
import software.amazon.awssdk.services.sns.model.SetTopicAttributesResponse;
import software.amazon.awssdk.services.sns.model.Topic;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class AmazonSNSClientMock implements SnsClient {

    private static final String DEFAULT_TOPIC_ARN = "arn:aws:sns:us-east-1:541925086079:MyTopic";

    public AmazonSNSClientMock() {
    }

    @Override
    public SetTopicAttributesResponse setTopicAttributes(SetTopicAttributesRequest setTopicAttributesRequest) {
        assertEquals(DEFAULT_TOPIC_ARN, setTopicAttributesRequest.topicArn());
        assertEquals("Policy", setTopicAttributesRequest.attributeName());
        assertEquals("XXX", setTopicAttributesRequest.attributeValue());
        return SetTopicAttributesResponse.builder().build();
    }

    @Override
    public CreateTopicResponse createTopic(CreateTopicRequest createTopicRequest) {
        return CreateTopicResponse.builder().topicArn(DEFAULT_TOPIC_ARN).build();
    }

    @Override
    public PublishResponse publish(PublishRequest publishRequest) {
        return PublishResponse.builder().messageId("dcc8ce7a-7f18-4385-bedd-b97984b4363c").build();
    }

    @Override
    public ListTopicsResponse listTopics(ListTopicsRequest listTopicRequest) {
        ListTopicsResponse.Builder res = ListTopicsResponse.builder();
        Topic topic = Topic.builder().topicArn(DEFAULT_TOPIC_ARN).build();
        List<Topic> list = new ArrayList<>();
        list.add(topic);
        res.topics(list);
        return res.build();
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
