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
 *
 */
public class ThreadsDSLTest extends GroovyRendererTestSupport {

    public void testThreadsAsyncDeadLetterChannel() throws Exception {
        String dsl = "errorHandler(deadLetterChannel(\"mock://dead\").maximumRedeliveries(2).redeliverDelay(0).logStackTrace(false).handled(false));"
            + "from(\"direct:start\").threads(3).to(\"mock:result\")";
        String expected = "errorHandler(deadLetterChannel(\"mock://dead\").maximumRedeliveries(2).redeliverDelay(0).handled(false));"
            + "from(\"direct:start\").threads(3).to(\"mock:result\")";

        assertEquals(expected, render(dsl));
    }

    public void testThreadsAsyncRoute() throws Exception {
        String dsl = "from(\"direct:start\").transform(body().append(\" World\")).threads().to(\"mock:result\")";
        assertEquals(dsl, render(dsl));
    }

    public void testThreadsAsyncRouteNoWait() throws Exception {
        String dsl = "from(\"direct:start\").transform(body().append(\" World\")).threads().waitForTaskToComplete(WaitForTaskToComplete.Never).to(\"mock:result\")";
        assertEquals(dsl, render(dsl));
    }

    public void testThreadsAsyncRouteWaitIfReplyExpected() throws Exception {
        String dsl = "from(\"direct:start\").transform(body().append(\" World\")).threads().waitForTaskToComplete(WaitForTaskToComplete.IfReplyExpected).to(\"mock:result\")";
        String expected = "from(\"direct:start\").transform(body().append(\" World\")).threads().to(\"mock:result\")";

        assertEquals(expected, render(dsl));
    }
}
