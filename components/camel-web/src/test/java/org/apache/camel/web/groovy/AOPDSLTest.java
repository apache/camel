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
public class AOPDSLTest extends GroovyRendererTestSupport {

    public void testAOPAfter() throws Exception {
        String dsl = "from(\"direct:start\").aop().after(\"mock:after\").transform(constant(\"Bye World\")).to(\"mock:result\")";
        assertEquals(dsl, render(dsl));
    }

    // TODO: fix this test!
    public void fixmeTestAOPAfterFinally() throws Exception {
        String dsl = "from(\"direct:start\").aop().afterFinally(\"mock:after\").choice()"
            + ".when(body().isEqualTo(\"Hello World\")).transform(constant(\"Bye World\"))"
            + ".otherwise().transform(constant(\"Kabom the World\")).throwException(new IllegalArgumentException(\"Damn\"))"
            + ".end().to(\"mock:result\")";
        assertEquals(dsl, render(dsl));
    }

    public void testAOPAround() throws Exception {
        String dsl = "from(\"direct:start\").aop().around(\"mock:before\", \"mock:after\").transform(constant(\"Bye World\")).to(\"mock:result\")";
        assertEquals(dsl, render(dsl));
    }

    // TODO: fix this test!
    public void fixmeTestAOPAroundFinally() throws Exception {
        String dsl = "from(\"direct:start\").aop().aroundFinally(\"mock:before\", \"mock:after\").choice()"
            + ".when(body().isEqualTo(\"Hello World\")).transform(constant(\"Bye World\"))"
            + ".otherwise().transform(constant(\"Kabom the World\")).throwException(new IllegalArgumentException(\"Damn\"))"
            + ".end()to(\"mock:result\")";
        assertEquals(dsl, render(dsl));
    }

    public void testAOPBefore() throws Exception {
        String dsl = "from(\"direct:start\").aop().before(\"mock:before\").transform(constant(\"Bye World\")).to(\"mock:result\")";
        assertEquals(dsl, render(dsl));
    }

    public void testAOPNestedRoute() throws Exception {
        String dsl = "from(\"direct:start\").to(\"mock:start\").aop().around(\"mock:before\", \"mock:after\")"
            + ".transform(constant(\"Bye\")).to(\"mock:middle\").transform(body().append(\" World\")).end().transform(body().prepend(\"Bye \")).to(\"mock:result\")";
        String expected = "from(\"direct:start\").to(\"mock:start\").aop().around(\"mock:before\", \"mock:after\")"
            + ".transform(constant(\"Bye\")).to(\"mock:middle\").transform(body().append(\" World\")).transform(body().prepend(\"Bye \")).to(\"mock:result\")";

        assertEquals(expected, render(dsl));
    }

}
