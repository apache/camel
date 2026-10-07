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
package org.apache.camel.component.docling;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

import ai.docling.serve.api.convert.response.ConvertDocumentResponse;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for the docling consumer failure path: a conversion that completed exceptionally is emitted as one exchange
 * carrying the underlying exception, which the route's onException handles.
 */
class DoclingConsumerFailureTest extends CamelTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("docling:onComplete?initialDelay=0&delay=100")
                        .onException(Exception.class).handled(true).to("mock:failed").end()
                        .to("mock:result");
            }
        };
    }

    private Map<String, AsyncTaskEntry> pendingAsyncTasks() {
        return context.getComponent("docling", DoclingComponent.class).getPendingAsyncTasks();
    }

    @Test
    void routesAFailedConversionToOnException() throws Exception {
        MockEndpoint failed = getMockEndpoint("mock:failed");
        failed.expectedMessageCount(1);
        failed.expectedHeaderReceived(DoclingHeaders.TASK_ID, "task-failure");
        getMockEndpoint("mock:result").expectedMessageCount(0);

        CompletableFuture<ConvertDocumentResponse> future = new CompletableFuture<>();
        future.completeExceptionally(new RuntimeException("conversion failed"));
        pendingAsyncTasks().put("task-failure", new AsyncTaskEntry("task-failure", future));

        MockEndpoint.assertIsSatisfied(context);
        assertTrue(pendingAsyncTasks().isEmpty(), "the failed task should be drained from the pending map");
    }
}
