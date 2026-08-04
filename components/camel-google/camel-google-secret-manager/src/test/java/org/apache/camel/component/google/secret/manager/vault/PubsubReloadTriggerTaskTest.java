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
package org.apache.camel.component.google.secret.manager.vault;

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.cloud.pubsub.v1.AckReplyConsumer;
import com.google.pubsub.v1.PubsubMessage;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.support.DefaultContextReloadStrategy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PubsubReloadTriggerTaskTest {

    private static final class CountingReloadStrategy extends DefaultContextReloadStrategy {
        private final AtomicInteger reloads = new AtomicInteger();

        @Override
        public void onReload(Object source) {
            reloads.incrementAndGet();
        }
    }

    private static final class RecordingAck implements AckReplyConsumer {
        private boolean acked;

        @Override
        public void ack() {
            acked = true;
        }

        @Override
        public void nack() {
        }
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field f = PubsubReloadTriggerTask.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }

    private static Object getField(Object target, String name) throws Exception {
        Field f = PubsubReloadTriggerTask.class.getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }

    private static PubsubMessage message(String eventType, String secretId) {
        PubsubMessage.Builder builder = PubsubMessage.newBuilder();
        if (eventType != null) {
            builder.putAttributes("eventType", eventType);
        }
        if (secretId != null) {
            builder.putAttributes("secretId", secretId);
        }
        return builder.build();
    }

    @Test
    public void secretsAreReadFromTheGcpVaultConfiguration() throws Exception {
        DefaultCamelContext context = new DefaultCamelContext();
        context.getVaultConfiguration().gcp().setSecrets("gcp-secret");
        context.getVaultConfiguration().aws().setSecrets("aws-secret");

        PubsubReloadTriggerTask task = new PubsubReloadTriggerTask();
        task.setCamelContext(context);

        // no GCP credentials are configured, so starting the task fails once it reaches the gcp properties
        // function. By then the secrets to watch must already have been read from the GCP vault configuration
        assertThrows(RuntimeCamelException.class, task::start);
        assertEquals("gcp-secret", getField(task, "secrets"));
    }

    @Test
    public void reloadIsTriggeredOnlyForMatchingSecrets() throws Exception {
        DefaultCamelContext context = new DefaultCamelContext();
        CountingReloadStrategy reload = new CountingReloadStrategy();
        context.addService(reload);
        context.start();

        PubsubReloadTriggerTask task = new PubsubReloadTriggerTask();
        task.setCamelContext(context);
        setField(task, "secrets", "tracked");

        PubsubReloadTriggerTask.FilteringEventMessageReceiver receiver = task.new FilteringEventMessageReceiver();

        RecordingAck matching = new RecordingAck();
        receiver.receiveMessage(message("SECRET_UPDATE", "projects/p/secrets/tracked"), matching);
        assertEquals(1, reload.reloads.get());
        assertTrue(matching.acked);
        assertTrue(task.getUpdates().containsKey("tracked"));

        // a message for a secret that is not watched must not trigger another reload
        RecordingAck other = new RecordingAck();
        receiver.receiveMessage(message("SECRET_UPDATE", "projects/p/secrets/other"), other);
        assertEquals(1, reload.reloads.get());
        assertTrue(other.acked);

        // neither must an event type that is not a secret update
        RecordingAck unrelated = new RecordingAck();
        receiver.receiveMessage(message("SECRET_DELETE", "projects/p/secrets/tracked"), unrelated);
        assertEquals(1, reload.reloads.get());
        assertTrue(unrelated.acked);

        context.stop();
    }

    @Test
    public void messagesWithoutAttributesAreAcknowledged() throws Exception {
        DefaultCamelContext context = new DefaultCamelContext();
        PubsubReloadTriggerTask task = new PubsubReloadTriggerTask();
        task.setCamelContext(context);
        setField(task, "secrets", "tracked");

        PubsubReloadTriggerTask.FilteringEventMessageReceiver receiver = task.new FilteringEventMessageReceiver();

        // a message published on the subscription by something other than the secret manager event feed
        RecordingAck noAttributes = new RecordingAck();
        assertDoesNotThrow(() -> receiver.receiveMessage(message(null, null), noAttributes));
        assertTrue(noAttributes.acked);

        RecordingAck noSecretId = new RecordingAck();
        assertDoesNotThrow(() -> receiver.receiveMessage(message("SECRET_UPDATE", null), noSecretId));
        assertTrue(noSecretId.acked);
    }

    @Test
    public void runWithoutSubscriberDoesNotThrow() {
        PubsubReloadTriggerTask task = new PubsubReloadTriggerTask();
        task.setCamelContext(new DefaultCamelContext());

        assertDoesNotThrow(task::run);
        assertTrue(task.getLastCheckTime() != null);
    }
}
