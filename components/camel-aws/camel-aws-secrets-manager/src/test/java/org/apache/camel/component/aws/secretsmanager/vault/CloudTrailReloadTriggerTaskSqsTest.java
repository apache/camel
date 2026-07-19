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
package org.apache.camel.component.aws.secretsmanager.vault;

import java.lang.reflect.Field;

import org.apache.camel.impl.DefaultCamelContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class CloudTrailReloadTriggerTaskSqsTest {

    @Mock
    private SqsClient sqsClient;

    private static String event(String secretId) {
        return "{\"detail\":{\"eventSource\":\"secretsmanager.amazonaws.com\",\"eventName\":\"PutSecretValue\","
               + "\"requestParameters\":{\"secretId\":\"" + secretId + "\"},\"eventTime\":\"2026-01-01T00:00:00Z\"}}";
    }

    private CloudTrailReloadTriggerTask task(String secrets) throws Exception {
        CloudTrailReloadTriggerTask task = new CloudTrailReloadTriggerTask();
        task.setCamelContext(new DefaultCamelContext());
        setField(task, "useSqsNotification", true);
        setField(task, "queueUrl", "http://localhost/queue");
        setField(task, "sqsClient", sqsClient);
        setField(task, "secrets", secrets);
        return task;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = CloudTrailReloadTriggerTask.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static Message message(String secretId, String receiptHandle) {
        return Message.builder().body(event(secretId)).receiptHandle(receiptHandle).build();
    }

    @Test
    public void drainsEveryExaminedMessageIncludingNonMatching() throws Exception {
        CloudTrailReloadTriggerTask task = task("tracked-secret");
        // One message matches the tracked secret, one does not. Both must be deleted so the queue is drained.
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder()
                        .messages(message("other-secret", "rh1"), message("tracked-secret", "rh2"))
                        .build());

        task.run();

        verify(sqsClient, times(2)).deleteMessage(any(DeleteMessageRequest.class));
    }

    @Test
    public void doesNotThrowOrDeleteWhenQueueDoesNotExist() throws Exception {
        CloudTrailReloadTriggerTask task = task("tracked-secret");
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenThrow(QueueDoesNotExistException.builder().message("missing").build());

        // A missing queue must not lead to a NullPointerException on the (null) receive result.
        assertDoesNotThrow(task::run);
        verify(sqsClient, never()).deleteMessage(any(DeleteMessageRequest.class));
    }
}
