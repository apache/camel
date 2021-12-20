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
package org.apache.camel.component.ignite;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.ignite.compute.IgniteComputeComponent;
import org.apache.camel.util.ObjectHelper;
import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.events.EventType;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;

import static org.junit.jupiter.api.Assertions.fail;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisabledOnOs(OS.MAC)
public class IgniteComputeTest extends AbstractIgniteTest {

    private static final List<Ignite> ADDITIONAL_INSTANCES = Lists.newArrayList();
    private static final List<UUID> LISTENERS = Lists.newArrayList();

    @Override
    protected String getScheme() {
        return "ignite-compute";
    }

    @Override
    protected AbstractIgniteComponent createComponent() {
        return IgniteComputeComponent.fromConfiguration(createConfiguration());
    }

    @Test
    public void testExecuteWithWrongPayload() {
        try {
            template.requestBody("ignite-compute:" + resourceUid + "?executionType=EXECUTE",
                    TestIgniteComputeResources.TEST_CALLABLE, String.class);
        } catch (Exception e) {
            Assertions.assertThat(ObjectHelper.getException(RuntimeCamelException.class, e).getMessage())
                    .startsWith("Ignite Compute endpoint with EXECUTE");
            return;
        }

        fail();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testCall() {
        TestIgniteComputeResources.COUNTER.set(0);

        // Single Callable.
        String result = template.requestBody("ignite-compute:" + resourceUid + "?executionType=CALL",
                TestIgniteComputeResources.TEST_CALLABLE, String.class);

        Assertions.assertThat(result).isEqualTo("hello");

        // Collection of Callables.
        Object[] callables = new Object[5];
        Arrays.fill(callables, TestIgniteComputeResources.TEST_CALLABLE);
        Collection<String> colResult = template.requestBody("ignite-compute:" + resourceUid + "?executionType=CALL",
                Lists.newArrayList(callables), Collection.class);

        Assertions.assertThat(colResult).containsExactly("hello", "hello", "hello", "hello", "hello");

        // Callables with a Reducer.
        String reduced = template.requestBodyAndHeader("ignite-compute:" + resourceUid + "?executionType=CALL",
                Lists.newArrayList(callables),
                IgniteConstants.IGNITE_COMPUTE_REDUCER, TestIgniteComputeResources.STRING_JOIN_REDUCER, String.class);

        Assertions.assertThat(reduced).isEqualTo("hellohellohellohellohello");
    }

    @Test
    public void testRun() {
        TestIgniteComputeResources.COUNTER.set(0);

        // Single Runnable.
        Object result = template.requestBody("ignite-compute:" + resourceUid + "?executionType=RUN",
                TestIgniteComputeResources.TEST_RUNNABLE_COUNTER, Object.class);
        Assertions.assertThat(result).isNull();
        Assertions.assertThat(TestIgniteComputeResources.COUNTER.get()).isEqualTo(1);

        // Multiple Runnables.
        Object[] runnables = new Object[5];
        Arrays.fill(runnables, TestIgniteComputeResources.TEST_RUNNABLE_COUNTER);
        result = template.requestBody("ignite-compute:" + resourceUid + "?executionType=RUN", Lists.newArrayList(runnables),
                Collection.class);
        Assertions.assertThat(result).isNull();
        Assertions.assertThat(TestIgniteComputeResources.COUNTER.get()).isEqualTo(6);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testBroadcast() {
        TestIgniteComputeResources.COUNTER.set(0);

        startAdditionalGridInstance();
        startAdditionalGridInstance();

        ignite().events().enableLocal(EventType.EVT_JOB_FINISHED);
        LISTENERS.add(
                ignite().events().remoteListen(null, TestIgniteComputeResources.EVENT_COUNTER, EventType.EVT_JOB_FINISHED));

        // Single Runnable.
        Object result = template.requestBody("ignite-compute:" + resourceUid + "?executionType=BROADCAST",
                TestIgniteComputeResources.TEST_RUNNABLE, Object.class);
        Assertions.assertThat(result).isNull();
        Assertions.assertThat(TestIgniteComputeResources.COUNTER.get()).isEqualTo(3);

        // Single Callable.
        Collection<String> colResult = template.requestBody("ignite-compute:" + resourceUid + "?executionType=BROADCAST",
                TestIgniteComputeResources.TEST_CALLABLE,
                Collection.class);
        Assertions.assertThat(colResult).isNotNull().containsExactly("hello", "hello", "hello");

        // Single Closure.
        colResult = template.requestBodyAndHeader("ignite-compute:" + resourceUid + "?executionType=BROADCAST",
                TestIgniteComputeResources.TEST_CLOSURE,
                IgniteConstants.IGNITE_COMPUTE_PARAMS, "Camel", Collection.class);
        Assertions.assertThat(colResult).isNotNull().containsExactly("hello Camel", "hello Camel", "hello Camel");
    }

    @Test
    public void testExecute() {
        TestIgniteComputeResources.COUNTER.set(0);

        startAdditionalGridInstance();
        startAdditionalGridInstance();

        ignite().events().enableLocal(EventType.EVT_JOB_RESULTED);
        LISTENERS.add(
                ignite().events().remoteListen(null, TestIgniteComputeResources.EVENT_COUNTER, EventType.EVT_JOB_RESULTED));

        // ComputeTask instance.
        String result = template.requestBodyAndHeader("ignite-compute:" + resourceUid + "?executionType=EXECUTE",
                TestIgniteComputeResources.COMPUTE_TASK,
                IgniteConstants.IGNITE_COMPUTE_PARAMS, 10, String.class);
        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(Splitter.on(",").splitToList(result)).contains("a0", "a1", "a2", "a3", "a4", "a5", "a6", "a7",
                "a8", "a9");

        // ComputeTask class.
        result = template.requestBodyAndHeader("ignite-compute:" + resourceUid + "?executionType=EXECUTE",
                TestIgniteComputeResources.COMPUTE_TASK.getClass(),
                IgniteConstants.IGNITE_COMPUTE_PARAMS, 10, String.class);
        Assertions.assertThat(result).isNotNull();
        Assertions.assertThat(Splitter.on(",").splitToList(result)).contains("a0", "a1", "a2", "a3", "a4", "a5", "a6", "a7",
                "a8", "a9");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testApply() {
        TestIgniteComputeResources.COUNTER.set(0);

        // Closure with a single parameter.
        String result = template.requestBodyAndHeader("ignite-compute:" + resourceUid + "?executionType=APPLY",
                TestIgniteComputeResources.TEST_CLOSURE,
                IgniteConstants.IGNITE_COMPUTE_PARAMS, "Camel", String.class);
        Assertions.assertThat(result).isEqualTo("hello Camel");

        // Closure with a Collection of parameters.
        Collection<String> colResult = template.requestBodyAndHeader("ignite-compute:" + resourceUid + "?executionType=APPLY",
                TestIgniteComputeResources.TEST_CLOSURE,
                IgniteConstants.IGNITE_COMPUTE_PARAMS, Lists.newArrayList("Camel1", "Camel2", "Camel3"), Collection.class);
        Assertions.assertThat(colResult).contains("hello Camel1", "hello Camel2", "hello Camel3");

        // Closure with a Collection of parameters and a Reducer.
        Map<String, Object> headers = ImmutableMap.<String, Object> of(IgniteConstants.IGNITE_COMPUTE_PARAMS,
                Lists.newArrayList("Camel1", "Camel2", "Camel3"),
                IgniteConstants.IGNITE_COMPUTE_REDUCER, TestIgniteComputeResources.STRING_JOIN_REDUCER);
        result = template.requestBodyAndHeaders("ignite-compute:" + resourceUid + "?executionType=APPLY",
                TestIgniteComputeResources.TEST_CLOSURE, headers, String.class);
        Assertions.assertThat(result).isEqualTo("hello Camel1hello Camel2hello Camel3");
    }

    private void startAdditionalGridInstance() {
        ADDITIONAL_INSTANCES.add(Ignition.start(createConfiguration()));
    }

    @AfterEach
    public void stopAdditionalIgniteInstances() {
        for (Ignite ignite : ADDITIONAL_INSTANCES) {
            ignite.close();
        }
        ADDITIONAL_INSTANCES.clear();
    }

    @AfterEach
    public void stopRemoteListeners() {
        for (UUID uuid : LISTENERS) {
            ignite().events().stopRemoteListen(uuid);
        }
        LISTENERS.clear();
    }

}
