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
package org.apache.camel.component.pqc;

import java.security.Security;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Produce;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A Camel producer is a singleton invoked concurrently, while {@code java.security.Signature} carries state across its
 * init - update - sign/verify sequence. Every message signed and then verified through the same pair of producers must
 * verify, no matter how many exchanges are in flight at once.
 */
class PQCConcurrentSignatureIsolationTest extends CamelTestSupport {

    private static final int THREADS = 8;
    private static final int MESSAGES_PER_THREAD = 40;

    @Produce("direct:sign")
    protected ProducerTemplate templateSign;

    @BeforeAll
    static void startup() {
        Security.addProvider(new BouncyCastleProvider());
    }

    @Test
    void everyConcurrentlySignedMessageVerifies() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            List<Callable<List<Object>>> work = new ArrayList<>();
            for (int t = 0; t < THREADS; t++) {
                final int thread = t;
                work.add(() -> {
                    List<Object> verdicts = new ArrayList<>();
                    for (int i = 0; i < MESSAGES_PER_THREAD; i++) {
                        verdicts.add(templateSign.requestBody("direct:sign", "message-" + thread + "-" + i, Object.class));
                    }
                    return verdicts;
                });
            }

            List<Object> allVerdicts = new ArrayList<>();
            for (Future<List<Object>> f : pool.invokeAll(work, 5, TimeUnit.MINUTES)) {
                allVerdicts.addAll(f.get());
            }

            assertThat(allVerdicts).hasSize(THREADS * MESSAGES_PER_THREAD);
            assertThat(allVerdicts).allSatisfy(v -> assertThat(v).isEqualTo(Boolean.TRUE));
        } finally {
            pool.shutdownNow();
        }
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:sign")
                        .to("pqc:sign?operation=sign&signatureAlgorithm=MLDSA")
                        .to("pqc:verify?operation=verify&signatureAlgorithm=MLDSA")
                        .setBody(header(PQCConstants.VERIFY));
            }
        };
    }
}
