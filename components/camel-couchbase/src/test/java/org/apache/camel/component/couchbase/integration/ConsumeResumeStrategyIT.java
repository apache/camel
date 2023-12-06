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
package org.apache.camel.component.couchbase.integration;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.processor.resume.TransientResumeStrategy;
import org.apache.camel.resume.ResumeAction;
import org.apache.camel.resume.ResumeActionAware;
import org.apache.camel.support.resume.Resumables;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Tags;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperties;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

import static org.apache.camel.component.couchbase.CouchbaseConstants.COUCHBASE_RESUME_ACTION;
import static org.awaitility.Awaitility.await;

@DisabledIfSystemProperties({
        @DisabledIfSystemProperty(named = "ci.env.name", matches = "apache.org",
                                  disabledReason = "Apache CI nodes are too resource constrained for this test"),
        @DisabledIfSystemProperty(named = "ci.env.name", matches = "github.com", disabledReason = "Flaky on GitHub Actions"),
        @DisabledIfSystemProperty(named = "couchbase.enable.it", matches = "false",
                                  disabledReason = "Too resource intensive for most systems to run reliably"),
})
@Tags({ @Tag("couchbase-71") })
public class ConsumeResumeStrategyIT extends CouchbaseIntegrationTestBase {
    static class TestCouchbaseResumeAdapter implements ResumeActionAware {
        volatile boolean setResumeActionCalled;
        volatile boolean resumeActionNotNull;
        volatile boolean resumeCalled;

        @Override
        public void resume() {
            resumeCalled = true;
        }

        @Override
        public void setResumeAction(ResumeAction resumeAction) {
            setResumeActionCalled = true;
            resumeActionNotNull = resumeAction != null;
        }
    }

    private final TransientResumeStrategy resumeStrategy = new TransientResumeStrategy(new TestCouchbaseResumeAdapter());

    @BeforeEach
    public void addToBucket() {
        for (int i = 0; i < 15; i++) {
            cluster.bucket(bucketName).defaultCollection().upsert("DocumentID_" + i, "message" + i);
        }
    }

    @Test
    public void testQueryForBeers() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedMessageCount(10);

        MockEndpoint.assertIsSatisfied(context, 30, TimeUnit.SECONDS);

        TestCouchbaseResumeAdapter adapter = resumeStrategy.getAdapter(TestCouchbaseResumeAdapter.class);
        await().atMost(30, TimeUnit.SECONDS).until(() -> adapter != null);

        await().atMost(30, TimeUnit.SECONDS)
                .untilAsserted(() -> Assertions.assertTrue(adapter.setResumeActionCalled,
                        "The setBucket method should have been called"));
        await().atMost(3, TimeUnit.SECONDS)
                .untilAsserted(() -> Assertions.assertTrue(adapter.resumeActionNotNull,
                        "The input bucket should not have been null"));
        await().atMost(3, TimeUnit.SECONDS)
                .untilAsserted(
                        () -> Assertions.assertTrue(adapter.resumeCalled, "The resume method should have been called"));
    }

    @AfterEach
    public void cleanBucket() {
        cluster.buckets().flushBucket(bucketName);
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                bindToRegistry(COUCHBASE_RESUME_ACTION, (ResumeAction) (key, value) -> true);

                from(String.format("%s&designDocumentName=%s&viewName=%s&limit=10", getConnectionUri(), bucketName, bucketName))
                        .resumable().resumeStrategy(resumeStrategy)
                        .setHeader(Exchange.OFFSET,
                                constant(Resumables.of("key", ThreadLocalRandom.current().nextInt(1, 1000))))
                        .log("message received")
                        .to("mock:result");
            }
        };

    }
}
