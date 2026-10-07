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
import ai.docling.serve.api.convert.response.DocumentResponse;
import ai.docling.serve.api.convert.response.InBodyConvertDocumentResponse;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test for the docling consumer success path: a completed async conversion is drained from the component's pending
 * task map and emitted as one exchange whose body is the converted content.
 */
class DoclingConsumerTest extends CamelTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("docling:onComplete?initialDelay=0&delay=100")
                        .to("mock:result");
            }
        };
    }

    private Map<String, AsyncTaskEntry> pendingAsyncTasks() {
        return context.getComponent("docling", DoclingComponent.class).getPendingAsyncTasks();
    }

    @Test
    void emitsCompletedConversionToTheRoute() throws Exception {
        MockEndpoint result = getMockEndpoint("mock:result");
        result.expectedMessageCount(1);
        result.expectedBodiesReceived("# Converted Document");
        result.expectedHeaderReceived(DoclingHeaders.TASK_ID, "task-success");

        ConvertDocumentResponse response = InBodyConvertDocumentResponse.builder()
                .document(DocumentResponse.builder().markdownContent("# Converted Document").build())
                .build();
        pendingAsyncTasks().put("task-success",
                new AsyncTaskEntry("task-success", CompletableFuture.completedFuture(response)));

        result.assertIsSatisfied();
        assertTrue(pendingAsyncTasks().isEmpty(), "the completed task should be drained from the pending map");
    }
}
