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

package org.apache.camel.web.groovy;

/**
 * a test case for some deadLetter DSL: errorhandler(deadLetter()).to()
 */
public class DeadLetterDSLTest extends GroovyRendererTestSupport {

    public void testDeadLetterWithDefaultRedeliverDelay() throws Exception {
        String dsl = "errorHandler(deadLetterChannel(\"mock:failed\").maximumRedeliveries(0).handled(false));from(\"direct:start\").to(\"mock:result\")";
        String expected = "errorHandler(deadLetterChannel(\"mock://failed\").maximumRedeliveries(0).redeliverDelay(1000).handled(false));from(\"direct:start\").to(\"mock:result\")";

        assertEquals(expected, render(dsl));
    }

    public void testDeadLetterWithDefaultHandled() throws Exception {
        String dsl = "errorHandler(deadLetterChannel(\"mock:failed\").maximumRedeliveries(3).redeliverDelay(5000));from(\"direct:start\").to(\"mock:result\")";
        String expected = "errorHandler(deadLetterChannel(\"mock://failed\").maximumRedeliveries(3).redeliverDelay(5000).handled(true));from(\"direct:start\").to(\"mock:result\")";

        assertEquals(expected, render(dsl));
    }

    public void testDeadLetterDSL() throws Exception {
        String dsl = "errorHandler(deadLetterChannel(\"mock:failed\").maximumRedeliveries(3).redeliverDelay(5000).handled(false));from(\"direct:start\").to(\"mock:result\")";
        String expected = "errorHandler(deadLetterChannel(\"mock://failed\").maximumRedeliveries(3).redeliverDelay(5000).handled(false));from(\"direct:start\").to(\"mock:result\")";

        assertEquals(expected, render(dsl));
    }
}
