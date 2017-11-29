/**
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

import org.apache.camel.CamelContext;
import org.apache.camel.LoggingLevel;
import org.apache.camel.component.aws.xray.TestDataBuilder.TestTrace;
import org.apache.camel.processor.interceptor.Tracer;
import org.apache.camel.spi.InterceptStrategy;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Rule;

public class CamelAwsXRayTestSupport extends CamelTestSupport {

    @Rule
    public FakeAWSDaemon socketListener = new FakeAWSDaemon();

    private List<TestTrace> testData;

    public CamelAwsXRayTestSupport(TestTrace... testData) {
        this.testData = Arrays.asList(testData);
    }

    @Override
    protected void postProcessTest() throws Exception {
        super.postProcessTest();
        socketListener.getReceivedData().clear();
    }

    @Override
    protected void resetMocks() {
        super.resetMocks();
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();

        context.setTracing(true);
        final Tracer tracer = new Tracer();
        tracer.getDefaultTraceFormatter().setShowBody(false);
        tracer.setLogLevel(LoggingLevel.INFO);
        context.getInterceptStrategies().add(tracer);

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
        try {
            // give the socket listener a bit time to receive the data and transform it to Java objects
            Thread.sleep(500);
        } catch (InterruptedException iEx) {
            // ignore
        }
        Map<String, TestTrace> receivedData = socketListener.getReceivedData();
        TestUtils.checkData(receivedData, testData);
    }
}