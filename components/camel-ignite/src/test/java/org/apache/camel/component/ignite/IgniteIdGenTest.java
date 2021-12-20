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

import org.apache.camel.component.ignite.idgen.IgniteIdGenComponent;
import org.apache.camel.component.ignite.idgen.IgniteIdGenEndpoint;
import org.apache.camel.component.ignite.idgen.IgniteIdGenOperation;
import org.apache.ignite.IgniteAtomicSequence;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class IgniteIdGenTest extends AbstractIgniteTest {

    @Override
    protected String getScheme() {
        return "ignite-idgen";
    }

    @Override
    protected AbstractIgniteComponent createComponent() {
        return IgniteIdGenComponent.fromConfiguration(createConfiguration());
    }

    @Test
    public void testOperations() {
        Assertions
                .assertThat(
                        template.requestBody("ignite-idgen:" + resourceUid + "?initialValue=0&operation=GET", null, Long.class))
                .isZero();
        Assertions.assertThat(template
                .requestBody("ignite-idgen:" + resourceUid + "?initialValue=0&operation=GET_AND_INCREMENT", null, Long.class))
                .isZero();
        Assertions.assertThat(template
                .requestBody("ignite-idgen:" + resourceUid + "?initialValue=0&operation=INCREMENT_AND_GET", null, Long.class))
                .isEqualTo(2);
        Assertions.assertThat(
                template.requestBody("ignite-idgen:" + resourceUid + "?initialValue=0&operation=ADD_AND_GET", 5, Long.class))
                .isEqualTo(7);
        Assertions.assertThat(
                template.requestBody("ignite-idgen:" + resourceUid + "?initialValue=0&operation=GET_AND_ADD", 5, Long.class))
                .isEqualTo(7);
        Assertions
                .assertThat(
                        template.requestBody("ignite-idgen:" + resourceUid + "?initialValue=0&operation=GET", 5, Long.class))
                .isEqualTo(12);
    }

    @Test
    public void testInitialValue() {
        Assertions.assertThat(
                template.requestBody("ignite-idgen:" + resourceUid + "?operation=GET&initialValue=100", null, Long.class))
                .isEqualTo(100);
        Assertions
                .assertThat(template.requestBody(
                        "ignite-idgen:" + resourceUid + "?operation=GET_AND_INCREMENT&initialValue=100", null, Long.class))
                .isEqualTo(100);
        Assertions
                .assertThat(template.requestBody(
                        "ignite-idgen:" + resourceUid + "?operation=INCREMENT_AND_GET&initialValue=100", null, Long.class))
                .isEqualTo(102);
        Assertions.assertThat(
                template.requestBody("ignite-idgen:" + resourceUid + "?operation=ADD_AND_GET&initialValue=100", 5, Long.class))
                .isEqualTo(107);
        Assertions.assertThat(
                template.requestBody("ignite-idgen:" + resourceUid + "?operation=GET_AND_ADD&initialValue=100", 5, Long.class))
                .isEqualTo(107);
        Assertions
                .assertThat(
                        template.requestBody("ignite-idgen:" + resourceUid + "?operation=GET&initialValue=100", 5, Long.class))
                .isEqualTo(112);
    }

    @Test
    public void testDifferentOperation() {
        Assertions.assertThat(
                template.requestBody("ignite-idgen:" + resourceUid + "?operation=GET&initialValue=100", null, Long.class))
                .isEqualTo(100);
        Assertions
                .assertThat(template.requestBodyAndHeader(
                        "ignite-idgen:" + resourceUid + "?operation=GET_AND_INCREMENT&initialValue=100", null,
                        IgniteConstants.IGNITE_IDGEN_OPERATION,
                        IgniteIdGenOperation.INCREMENT_AND_GET, Long.class))
                .isEqualTo(101);
    }

    @Test
    public void testBatchSize() {
        IgniteIdGenEndpoint endpoint = context.getEndpoint(
                "ignite-idgen:" + resourceUid + "?operation=GET&initialValue=100&batchSize=100", IgniteIdGenEndpoint.class);
        Assertions.assertThat(template.requestBody(endpoint, null, Long.class)).isEqualTo(100);

        // Cannot test much here with a single Ignite instance, let's just test
        // that the parameter could be set.
        Assertions.assertThat(endpoint.getBatchSize()).isEqualTo(100);
    }

    @AfterEach
    public void deleteSets() {
        IgniteAtomicSequence seq = ignite().atomicSequence(resourceUid, 0, false);
        if (seq != null) {
            seq.close();
        }
    }
}
