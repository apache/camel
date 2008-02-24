/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.testng.example;

import java.util.Properties;

import org.apache.camel.testng.SpringRunner;
import org.apache.camel.component.mock.MockEndpoint;
import org.testng.annotations.Test;

/**
 * @version $Revision$
 */
@Test(groups = {"routing"})
public class MyTest extends SpringRunner {
    private String appContextLocation = " org/apache/camel/testng/example/spring.xml";

    public void useCaseFoo() throws Exception {
        assertApplicationContextStarts(appContextLocation, createProperties("useCase", "foo/input1"));
        assertExpectedCount(2);
    }

    public void useCaseBar() throws Exception {
        assertApplicationContextStarts(appContextLocation, createProperties("useCase", "bar/input1"));
        assertExpectedCount(1);
    }

    protected void assertExpectedCount(int expectedCount) throws InterruptedException {
        MockEndpoint endpoint = getCamelContext().getEndpoint("mock:results", MockEndpoint.class);
        endpoint.expectedMessageCount(expectedCount);
        endpoint.assertIsSatisfied();
    }

}
