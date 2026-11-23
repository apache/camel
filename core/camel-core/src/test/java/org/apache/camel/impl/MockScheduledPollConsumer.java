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
package org.apache.camel.impl;

import java.util.concurrent.ScheduledThreadPoolExecutor;

import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.camel.support.ScheduledPollConsumer;

public class MockScheduledPollConsumer extends ScheduledPollConsumer {

    private Exception exceptionToThrowOnPoll;
    private Error errorToThrowOnPoll;
    private int pollsUntilError;

    public MockScheduledPollConsumer(DefaultEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
    }

    // dummy constructor here - we just want to test the run() method, which
    // calls poll()
    public MockScheduledPollConsumer(Endpoint endpoint, Exception exceptionToThrowOnPoll) {
        super(endpoint, null, new ScheduledThreadPoolExecutor(1));
        this.exceptionToThrowOnPoll = exceptionToThrowOnPoll;
    }

    @Override
    protected int poll() throws Exception {
        if (errorToThrowOnPoll != null && --pollsUntilError == 0) {
            Error error = errorToThrowOnPoll;
            errorToThrowOnPoll = null;
            throw error;
        }
        if (exceptionToThrowOnPoll != null) {
            throw exceptionToThrowOnPoll;
        }
        return 0;
    }

    public void setExceptionToThrowOnPoll(Exception exceptionToThrowOnPoll) {
        this.exceptionToThrowOnPoll = exceptionToThrowOnPoll;
    }

    /**
     * Throws the error once, on the given poll from now on (1 is the next poll). The polls before and after it succeed.
     */
    public void setErrorToThrowOnPoll(Error errorToThrowOnPoll, int onPoll) {
        this.errorToThrowOnPoll = errorToThrowOnPoll;
        this.pollsUntilError = onPoll;
    }

    @Override
    public String toString() {
        return "MockScheduled";
    }
}
