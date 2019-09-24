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
package org.apache.camel.test.spring.junit5;

import org.apache.camel.util.StopWatch;
import org.apache.camel.util.TimeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.support.AbstractTestExecutionListener;

/**
 * An execution listener that simulates the timing output built in to
 * {@link org.apache.camel.test.junit5.CamelTestSupport}.
 */
public class StopWatchTestExecutionListener extends AbstractTestExecutionListener {

    protected static ThreadLocal<StopWatch> threadStopWatch = new ThreadLocal<>();

    /**
     * Returns the precedence that is used by Spring to choose the appropriate
     * execution order of test listeners.
     * 
     * See {@link SpringTestExecutionListenerSorter#getPrecedence(Class)} for more.
     */
    @Override
    public int getOrder() {
        return SpringTestExecutionListenerSorter.getPrecedence(getClass());
    }

    /**
     * Exists primarily for testing purposes, but allows for access to the
     * underlying stop watch instance for a test.
     */
    public static StopWatch getStopWatch() {
        return threadStopWatch.get();
    }

    @Override
    public void beforeTestMethod(TestContext testContext) throws Exception {
        StopWatch stopWatch = new StopWatch();
        threadStopWatch.set(stopWatch);
    }

    @Override
    public void afterTestMethod(TestContext testContext) throws Exception {
        StopWatch watch = threadStopWatch.get();
        if (watch != null) {
            long time = watch.taken();
            Logger log = LoggerFactory.getLogger(testContext.getTestClass());

            log.info("********************************************************************************");
            log.info("Testing done: " + testContext.getTestMethod().getName() + "(" + testContext.getTestClass().getName() + ")");
            log.info("Took: " + TimeUtils.printDuration(time) + " (" + time + " millis)");
            log.info("********************************************************************************");

            threadStopWatch.remove();
        }
    }

}
