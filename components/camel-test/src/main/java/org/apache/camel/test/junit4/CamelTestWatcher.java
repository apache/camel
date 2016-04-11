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
package org.apache.camel.test.junit4;

import org.apache.camel.util.StopWatch;
import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

/**
 * A JUnit {@link org.junit.rules.TestWatcher} that is used to time how long the test takes.
 */
public class CamelTestWatcher extends TestWatcher {

    private final StopWatch watch = new StopWatch();

    @Override
    protected void starting(Description description) {
        watch.restart();
    }

    @Override
    protected void finished(Description description) {
        watch.stop();
    }

    public long timeTaken() {
        return watch.taken();
    }

}
