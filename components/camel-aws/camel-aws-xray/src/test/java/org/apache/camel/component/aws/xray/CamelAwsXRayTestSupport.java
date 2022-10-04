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
package org.apache.camel.component.aws.xray;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.component.aws.xray.TestDataBuilder.TestTrace;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import static org.awaitility.Awaitility.await;

public class CamelAwsXRayTestSupport extends CamelTestSupport {

    protected FakeAWSDaemon socketListener = new FakeAWSDaemon();

    private List<TestTrace> testData;

    public CamelAwsXRayTestSupport(TestTrace... testData) {
        this.testData = Arrays.asList(testData);
    }

    @BeforeEach
    public void setUp() throws Exception {
        socketListener.before();
        super.setUp();
    }

    @AfterEach
    public void tearDown() throws Exception {
        super.tearDown();
        socketListener.after();
    }

    @Override
    protected void postProcessTest() throws Exception {
        super.postProcessTest();
        socketListener.getReceivedData().clear();
    }

    protected void resetMocks() {
        MockEndpoint.resetMocks(context);
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        context.setTracing(true);

        XRayTracer xRayTracer = new XRayTracer();
        xRayTracer.setCamelContext(context);
        xRayTracer.setTracingStrategy(getTracingStrategy());
        xRayTracer.setExcludePatterns(getExcludePatterns());

        xRayTracer.init(context);

        return context;
    }

    protected InterceptStrategy getTracingStrategy() {
        return new NoopTracingStrategy();
    }

    protected Set<String> getExcludePatterns() {
        return new HashSet<>();
    }

    protected void verify() {
        Map<String, TestTrace> receivedData = await().atMost(500, TimeUnit.MILLISECONDS)
                .until(socketListener::getReceivedData, v -> v.size() == testData.size());

        TestUtils.checkData(receivedData, testData);
    }
}
