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
package org.apache.camel.component.aws2.sqs.dst;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.apache.camel.test.dst.Dst;
import org.apache.camel.test.dst.DstLedger;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.BatchResultErrorEntry;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityBatchRequest;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityBatchRequestEntry;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityBatchResponse;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityBatchResultEntry;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageResponse;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiptHandleIsInvalidException;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

/**
 * A fake Amazon SQS standard queue, written from the SQS API reference, not from the Camel consumer. Each rule names
 * the page it comes from:
 * <ul>
 * <li>[R] ReceiveMessage: https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_ReceiveMessage.html
 * </li>
 * <li>[D] DeleteMessage: https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_DeleteMessage.html
 * </li>
 * <li>[C] ChangeMessageVisibility:
 * https://docs.aws.amazon.com/AWSSimpleQueueService/latest/APIReference/API_ChangeMessageVisibility.html</li>
 * </ul>
 * Not modelled: long polling (WaitTimeSeconds), FIFO queues, the in-flight limit, retention, dead-letter queues, and
 * the rare redelivery of a deleted message of a standard queue [D].
 * <p>
 * Where a call takes effect in time: the harness simulates threads on one stack, so a task blocked in a call resumes
 * only after the tasks that started blocking after it (see the report). To keep that from shortening the time the
 * consumer has before a visibility timeout runs out, receive picks its messages at the end of its latency, delete takes
 * effect at the start of its latency, and the visibility change does not block at all.
 */
final class FakeSqs implements SqsClient {

    static final String QUEUE_URL = "https://sqs.eu-west-1.amazonaws.com/000000000000/dst";

    /** [R] "If not specified, the default visibility timeout for the queue is used, which is 30 seconds." */
    private static final int DEFAULT_VISIBILITY_TIMEOUT = 30;

    static final class Msg {
        final String id;
        final String body;
        String handle;
        long invisibleUntil;
        boolean deleted;
        int receives;

        Msg(String id, String body) {
            this.id = id;
            this.body = body;
        }
    }

    private final Dst dst;
    private final DstLedger ledger;
    private final Map<String, Msg> messages = new LinkedHashMap<>();
    private final Map<String, Msg> byHandle = new HashMap<>();
    private final Consumer<Msg> onReceive;
    private final Consumer<List<String>> onExtensionFault;
    private long handles;
    boolean faults = true;

    FakeSqs(Dst dst, DstLedger ledger, Consumer<Msg> onReceive, Consumer<List<String>> onExtensionFault) {
        this.dst = dst;
        this.ledger = ledger;
        this.onReceive = onReceive;
        this.onExtensionFault = onExtensionFault;
    }

    void send(String id) {
        messages.put(id, new Msg(id, "body of " + id));
        ledger.record("sent", "q", id);
    }

    boolean allDeleted() {
        return messages.values().stream().allMatch(m -> m.deleted);
    }

    List<String> notDeleted() {
        return messages.values().stream().filter(m -> !m.deleted).map(m -> m.id).toList();
    }

    private boolean inFlight(Msg m) {
        // [C] "A message is considered to be in flight after it is received from a queue by a consumer, but not yet
        // deleted from the queue"; it is hidden until its visibility timeout runs out [R]
        return !m.deleted && m.handle != null && dst.now() < m.invisibleUntil;
    }

    @Override
    public ReceiveMessageResponse receiveMessage(ReceiveMessageRequest request) {
        long latency = dst.chance(0.1) ? dst.between(200, 1500) : dst.between(5, 80);
        dst.block(latency, "sqs ReceiveMessage max=" + request.maxNumberOfMessages());
        if (faults && dst.chance(0.05)) {
            ledger.record("fault", "q", "receive failed");
            throw Dst.injected(SdkClientException.create("dst: ReceiveMessage failed (connection reset)"));
        }
        List<Msg> visible = new ArrayList<>();
        for (Msg m : messages.values()) {
            if (!m.deleted && !inFlight(m)) {
                visible.add(m);
            }
        }
        // [R] "Valid values: 1 to 10. Default: 1." and "Amazon SQS never returns more messages than this value
        // (however, fewer messages might be returned)"
        int max = request.maxNumberOfMessages() != null ? request.maxNumberOfMessages() : 1;
        int upTo = Math.min(max, visible.size());
        // [R] short polling samples "a weighted random set of machines": "If the number of messages in the queue is
        // small (fewer than 1,000), you most likely get fewer messages than you requested", "you might not receive
        // any messages in a particular ReceiveMessage response"
        int n = upTo == 0 ? 0 : dst.chance(0.5) ? upTo : (int) dst.between(0, upTo);
        // [R] "The parameter is applied to the messages that Amazon SQS returns in the response"
        int vt = request.visibilityTimeout() != null ? request.visibilityTimeout() : DEFAULT_VISIBILITY_TIMEOUT;
        List<Message> answer = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Msg m = visible.remove(dst.nextInt(visible.size()));
            // [D] "Each time you receive a message ... it comes with a unique ReceiptHandle. If you receive the same
            // message more than once, you will get a different ReceiptHandle each time."
            m.handle = "rh-" + m.id + "-" + ++handles;
            byHandle.put(m.handle, m);
            m.invisibleUntil = dst.now() + vt * 1000L;
            m.receives++;
            ledger.record("received", "q", m.id + " #" + m.receives + " visible again at t=" + m.invisibleUntil);
            onReceive.accept(m);
            answer.add(Message.builder().messageId(m.id).body(m.body).receiptHandle(m.handle).md5OfBody("md5")
                    .attributes(Map.of(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT, String.valueOf(m.receives)))
                    .build());
        }
        return ReceiveMessageResponse.builder().messages(answer).build();
    }

    @Override
    public DeleteMessageResponse deleteMessage(DeleteMessageRequest request) {
        long latency = dst.between(5, 80);
        if (faults && dst.chance(0.05)) {
            // the request is lost: nothing is deleted
            dst.block(latency, "sqs DeleteMessage (fails)");
            ledger.record("fault", "q", "delete failed " + request.receiptHandle());
            throw Dst.injected(SdkClientException.create("dst: DeleteMessage failed (connection reset)"));
        }
        Msg m = byHandle.get(request.receiptHandle());
        if (m == null) {
            throw Dst.injected(
                    ReceiptHandleIsInvalidException.builder().message("The specified receipt handle isn't valid.").build());
        }
        if (!m.deleted) {
            boolean current = request.receiptHandle().equals(m.handle);
            if (current && inFlight(m)) {
                delete(m, "deleted");
            } else if (current) {
                // [R] "if a consumer whose message processing time is longer than the visibility timeout tries to
                // delete the processed messages, the action fails with an error" ([D] does not say so: either)
                if (dst.chance(0.5)) {
                    dst.block(latency, "sqs DeleteMessage (expired)");
                    ledger.record("delete-rejected", "q", m.id + " visibility timeout expired");
                    throw Dst.injected(ReceiptHandleIsInvalidException.builder()
                            .message("The receipt handle has expired.").build());
                }
                delete(m, "deleted (expired handle)");
            } else {
                // [D] "If you use an old ReceiptHandle, the request will succeed, but the message might not be
                // deleted."
                if (dst.chance(0.5)) {
                    delete(m, "deleted (old handle)");
                } else {
                    ledger.record("delete-ignored", "q", m.id + " old handle");
                }
            }
        }
        dst.block(latency, "sqs DeleteMessage " + m.id);
        return DeleteMessageResponse.builder().build();
    }

    private void delete(Msg m, String how) {
        m.deleted = true;
        ledger.record("deleted", "q", m.id);
        dst.trace("sqs " + how + " " + m.id);
    }

    @Override
    public ChangeMessageVisibilityBatchResponse changeMessageVisibilityBatch(ChangeMessageVisibilityBatchRequest request) {
        List<String> ids = new ArrayList<>();
        for (ChangeMessageVisibilityBatchRequestEntry e : request.entries()) {
            Msg m = byHandle.get(e.receiptHandle());
            ids.add(m != null ? m.id : e.receiptHandle());
        }
        if (faults && dst.chance(0.02)) {
            ledger.record("fault", "q", "visibility change failed for " + ids);
            onExtensionFault.accept(ids);
            throw Dst.injected(SdkClientException.create("dst: ChangeMessageVisibilityBatch failed (connection reset)"));
        }
        List<ChangeMessageVisibilityBatchResultEntry> ok = new ArrayList<>();
        List<BatchResultErrorEntry> failed = new ArrayList<>();
        for (ChangeMessageVisibilityBatchRequestEntry e : request.entries()) {
            Msg m = byHandle.get(e.receiptHandle());
            if (m == null || m.deleted || !e.receiptHandle().equals(m.handle)) {
                // [C] "ReceiptHandleIsInvalid: The specified receipt handle isn't valid." (assumed for a deleted
                // message and for the handle of an earlier receive; the page does not spell these cases out)
                failed.add(error(e, "ReceiptHandleIsInvalid", "The specified receipt handle isn't valid."));
            } else if (!inFlight(m)) {
                // [C] "MessageNotInflight: The specified message isn't in flight."
                failed.add(error(e, "MessageNotInflight", "The specified message isn't in flight."));
                ledger.record("extend-too-late", "q", m.id);
            } else {
                // [C] "the 10 seconds begin to count from the time that you make the ChangeMessageVisibility call"
                m.invisibleUntil = dst.now() + e.visibilityTimeout() * 1000L;
                ok.add(ChangeMessageVisibilityBatchResultEntry.builder().id(e.id()).build());
                ledger.record("extended", "q", m.id + " visible again at t=" + m.invisibleUntil);
            }
        }
        return ChangeMessageVisibilityBatchResponse.builder().successful(ok).failed(failed).build();
    }

    private static BatchResultErrorEntry error(ChangeMessageVisibilityBatchRequestEntry e, String code, String message) {
        return BatchResultErrorEntry.builder().id(e.id()).code(code).message(message).senderFault(true).build();
    }

    @Override
    public String serviceName() {
        return "sqs";
    }

    @Override
    public void close() {
        // nothing to close
    }
}
