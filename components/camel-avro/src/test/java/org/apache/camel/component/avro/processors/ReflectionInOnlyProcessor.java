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
package org.apache.camel.component.avro.processors;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.avro.test.TestPojo;
import org.apache.camel.avro.test.TestReflection;

public class ReflectionInOnlyProcessor implements Processor {

    private TestReflection testReflection;

    public ReflectionInOnlyProcessor(TestReflection testReflection) {
        this.testReflection = testReflection;
    }

    @Override
    public void process(Exchange exchange) throws Exception {
        Object body = exchange.getIn().getBody();
        if (body instanceof String) {
            testReflection.setName(String.valueOf(body));
        }
        if (body instanceof TestPojo) {
            testReflection.setTestPojo((TestPojo)body);
        }
    }

    public TestReflection getTestReflection() {
        return testReflection;
    }

    public void setTestReflection(TestReflection testReflection) {
        this.testReflection = testReflection;
    }

}
