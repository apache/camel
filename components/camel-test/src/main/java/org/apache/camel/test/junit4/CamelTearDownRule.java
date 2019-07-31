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
package org.apache.camel.test.junit4;

import org.junit.rules.ExternalResource;

/**
 * A JUnit rule to tear down Camel when using createCamelContextPerClass=true.
 */
public class CamelTearDownRule extends ExternalResource {

    private final ThreadLocal<CamelTestSupport> testSupport;

    public CamelTearDownRule(ThreadLocal<CamelTestSupport> testSupport) {
        this.testSupport = testSupport;
    }

    @Override
    protected void before() throws Throwable {
        super.before();
    }

    @Override
    protected void after() {
        CamelTestSupport support = testSupport.get();
        if (support != null && support.isCreateCamelContextPerClass()) {
            try {
                support.tearDownCreateCamelContextPerClass();
            } catch (Throwable e) {
                // ignore
            }
        }
        super.after();
    }
}
