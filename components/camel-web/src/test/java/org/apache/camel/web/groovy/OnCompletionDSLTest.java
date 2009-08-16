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
public class OnCompletionDSLTest extends GroovyRendererTestSupport {

    public void testOnCompletionAndIntercept() throws Exception {
        String dsl = "intercept().to(\"mock:intercept\");"
            + "from(\"direct:start\").onCompletion().to(\"log:sync\").to(\"mock:sync\").end().to(\"mock:result\")";
        assertEquals(dsl, render(dsl));
    }

    public void testOnCompletionAndInterceptAndOnException() throws Exception {
        String dsl = "intercept().to(\"mock:intercept\");"
            + "onCompletion().to(\"log:global\").to(\"mock:sync\");"
            + "onException(Exception.class).to(\"mock:exception\");"
            + "from(\"direct:start\").to(\"mock:result\")";
        // the order is changed
        String expected = "onException(Exception.class).to(\"mock:exception\");"
            + "intercept().to(\"mock:intercept\");"
            + "onCompletion().to(\"log:global\").to(\"mock:sync\");"
            + "from(\"direct:start\").to(\"mock:result\")";

        assertEquals(expected, render(dsl));
    }

    public void testOnCompletionDSL() throws Exception {
        String dsl = "from(\"direct:start\").onCompletion().to(\"log:sync\").to(\"mock:sync\").end().to(\"mock:result\")";
        assertEquals(dsl, render(dsl));
    }

    public void testOnCompletionGlobal() throws Exception {
        String dsl = "onCompletion().to(\"log:global\").to(\"mock:sync\");from(\"direct:start\").to(\"mock:result\")";
        assertEquals(dsl, render(dsl));
    }

    public void testOnCompletionMoreGlobal() throws Exception {
        String dsl = "onCompletion().to(\"log:global\").to(\"mock:sync\");"
            + "onCompletion().to(\"log:global\").to(\"mock:two\");"
            + "onCompletion().onCompleteOnly().to(\"log:global\").to(\"mock:complete\");"
            + "onCompletion().onFailureOnly().to(\"log:global\").to(\"mock:failure\");"
            + "from(\"direct:start\").to(\"mock:result\")";
        assertEquals(dsl, render(dsl));
    }

    public void testOnCompletionOnly() throws Exception {
        String dsl = "from(\"direct:start\").onCompletion().onCompleteOnly().to(\"log:sync\").to(\"mock:sync\").end().to(\"mock:result\")";
        assertEquals(dsl, render(dsl));
    }

    public void testOnCompletionRouteScopeOverrideGlobalScope() throws Exception {
        String dsl = "onCompletion().to(\"log:global\").to(\"mock:global\");"
            + "from(\"direct:start\").onCompletion().to(\"log:route\").to(\"mock:sync\").end().to(\"mock:result\")";
        // the global onCompletion is removed
        String expected = "from(\"direct:start\").onCompletion().to(\"log:route\").to(\"mock:sync\").end().to(\"mock:result\")";

        assertEquals(expected, render(dsl));
    }

    public void testOnCompletionWhen() throws Exception {
        String dsl = "from(\"direct:start\").onCompletion().onWhen(body().contains(\"Hello\")).to(\"log:sync\").to(\"mock:sync\").end().to(\"log:original\").to(\"mock:result\")";
        assertEquals(dsl, render(dsl));
    }

    public void testOnFailureOnly() throws Exception {
        String dsl = "from(\"direct:start\").onCompletion().onFailureOnly().to(\"log:sync\").to(\"mock:sync\").end().to(\"mock:result\")";
        assertEquals(dsl, render(dsl));
    }
}
