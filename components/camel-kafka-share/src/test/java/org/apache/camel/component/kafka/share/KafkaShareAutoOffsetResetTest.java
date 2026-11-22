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
package org.apache.camel.component.kafka.share;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AlterConfigOp;
import org.apache.kafka.clients.admin.AlterConfigsOptions;
import org.apache.kafka.clients.admin.AlterConfigsResult;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ShareConsumer;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.errors.GroupAuthorizationException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The shareAutoOffsetReset option sets the group configuration share.auto.offset.reset with the Admin client when the
 * consumer starts (CAMEL-27379), with an Admin mock instead of a broker.
 */
class KafkaShareAutoOffsetResetTest extends CamelTestSupport {

    private static final String TOPIC = "orders";

    private final List<Properties> adminProps = new CopyOnWriteArrayList<>();
    private final List<RecordingShareConsumer> consumers = new CopyOnWriteArrayList<>();
    private Admin admin;

    private final KafkaShareClientFactory factory = new KafkaShareClientFactory() {
        @Override
        public ShareConsumer<Object, Object> getShareConsumer(Properties kafkaProps) {
            RecordingShareConsumer consumer = new RecordingShareConsumer();
            consumers.add(consumer);
            return consumer;
        }

        @Override
        public Admin getAdmin(Properties kafkaProps) {
            adminProps.add(kafkaProps);
            return admin;
        }

        @Override
        public String getBrokers(KafkaShareConfiguration configuration) {
            return "localhost:9092";
        }
    };

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    void setsTheGroupConfigBeforeTheShareConsumersStart() throws Exception {
        admin = adminThatSucceeds();

        startRoute("shareAutoOffsetReset=earliest&saslJaasConfig=RAW(jaas)&securityProtocol=SASL_SSL"
                   + "&additionalProperties.my.custom=x");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<ConfigResource, Collection<AlterConfigOp>>> configs = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<AlterConfigsOptions> options = ArgumentCaptor.forClass(AlterConfigsOptions.class);
        verify(admin).incrementalAlterConfigs(configs.capture(), options.capture());
        verify(admin).close();

        ConfigResource group = new ConfigResource(ConfigResource.Type.GROUP, "my-group");
        assertThat(configs.getValue()).containsOnlyKeys(group);
        AlterConfigOp op = configs.getValue().get(group).iterator().next();
        assertThat(op.opType()).isEqualTo(AlterConfigOp.OpType.SET);
        assertThat(op.configEntry().name()).isEqualTo("share.auto.offset.reset");
        assertThat(op.configEntry().value()).isEqualTo("earliest");
        assertThat(options.getValue().timeoutMs()).isEqualTo(30000);

        // the admin client gets the brokers, the security and the additional properties, not the consumer settings
        Properties props = adminProps.get(0);
        assertThat(props).containsEntry(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092")
                .containsEntry(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, "SASL_SSL")
                .containsEntry("sasl.jaas.config", "jaas")
                .containsEntry("my.custom", "x")
                .doesNotContainKeys(ConsumerConfig.GROUP_ID_CONFIG, ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                        ConsumerConfig.MAX_POLL_RECORDS_CONFIG, ConsumerConfig.SHARE_ACKNOWLEDGEMENT_MODE_CONFIG);

        await().atMost(10, TimeUnit.SECONDS).until(() -> !consumers.isEmpty());
    }

    @Test
    void byDuration() throws Exception {
        admin = adminThatSucceeds();

        startRoute("shareAutoOffsetReset=by_duration:PT1H");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<ConfigResource, Collection<AlterConfigOp>>> configs = ArgumentCaptor.forClass(Map.class);
        verify(admin).incrementalAlterConfigs(configs.capture(), any(AlterConfigsOptions.class));
        assertThat(configs.getValue().values().iterator().next().iterator().next().configEntry().value())
                .isEqualTo("by_duration:PT1H");
    }

    @Test
    void withoutTheOptionTheGroupConfigIsLeftAsItIs() throws Exception {
        startRoute("pollTimeoutMs=100");

        await().atMost(10, TimeUnit.SECONDS).until(() -> !consumers.isEmpty());
        assertThat(adminProps).isEmpty();
    }

    @Test
    void consumerFailsToStartWhenTheConfigCannotBeSet() throws Exception {
        admin = mock(Admin.class);
        AlterConfigsResult result = mock(AlterConfigsResult.class);
        @SuppressWarnings("unchecked")
        KafkaFuture<Void> failed = mock(KafkaFuture.class);
        when(failed.get(anyLong(), any(TimeUnit.class))).thenThrow(
                new ExecutionException(new GroupAuthorizationException("Not authorized to access group: my-group")));
        when(result.all()).thenReturn(failed);
        when(admin.incrementalAlterConfigs(any(), any(AlterConfigsOptions.class))).thenReturn(result);

        assertThatThrownBy(() -> startRoute("shareAutoOffsetReset=earliest"))
                .rootCause()
                .isInstanceOf(GroupAuthorizationException.class);
        assertThatThrownBy(() -> startRoute("shareAutoOffsetReset=earliest"))
                .hasStackTraceContaining("Cannot set share.auto.offset.reset=earliest on the share group my-group"
                                         + " (the shareAutoOffsetReset option): Not authorized to access group: my-group."
                                         + " The principal of the consumer needs the ALTER_CONFIGS permission on the group.");
        verify(admin, atLeastOnce()).close();
        assertThat(consumers).isEmpty();
    }

    @Test
    void invalidValueFailsBeforeTheAdminClientIsCreated() {
        assertThatThrownBy(() -> startRoute("shareAutoOffsetReset=newest"))
                .hasStackTraceContaining(
                        "The shareAutoOffsetReset option must be earliest, latest or by_duration:<duration>, but was newest");
        assertThatThrownBy(() -> startRoute("shareAutoOffsetReset=by_duration:1h"))
                .hasStackTraceContaining("The shareAutoOffsetReset option by_duration:1h has an invalid duration 1h");
        assertThatThrownBy(() -> startRoute("shareAutoOffsetReset=by_duration:-PT1H"))
                .hasStackTraceContaining("has an invalid duration -PT1H");
        assertThat(adminProps).isEmpty();
    }

    private static Admin adminThatSucceeds() {
        Admin admin = mock(Admin.class);
        AlterConfigsResult result = mock(AlterConfigsResult.class);
        when(result.all()).thenReturn(KafkaFuture.completedFuture(null));
        when(admin.incrementalAlterConfigs(any(), any(AlterConfigsOptions.class))).thenReturn(result);
        return admin;
    }

    private void startRoute(String options) throws Exception {
        context.getRegistry().bind("adminFactory", factory);
        if (!context.isStarted()) {
            context.start();
        }
        String routeId = "route-" + context.getRoutes().size();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("kafka-share:" + TOPIC + "?brokers=localhost:9092&groupId=my-group&kafkaShareClientFactory=#adminFactory&"
                     + options)
                        .routeId(routeId)
                        .to("mock:result");
            }
        });
    }
}
