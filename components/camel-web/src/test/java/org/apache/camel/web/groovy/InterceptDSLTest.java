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
public class InterceptDSLTest extends GroovyRendererTestSupport {

    public void testIntercept() throws Exception {
        String DSL = "intercept().to(\"mock:intercepted\");from(\"direct:start\").to(\"mock:foo\").to(\"mock:bar\").to(\"mock:result\")";
        String expectedDSL = DSL;

        assertEquals(expectedDSL, render(DSL));
    }

    public void testInterceptStop() throws Exception {
        String DSL = "intercept().to(\"mock:intercepted\").stop();from(\"direct:start\").to(\"mock:foo\").to(\"mock:bar\").to(\"mock:result\")";
        String expectedDSL = DSL;

        assertEquals(expectedDSL, render(DSL));
    }

    public void testInterceptWhen() throws Exception {
        String DSL = "intercept().when(body().contains(\"Hello\")).to(\"mock:intercepted\");from(\"direct:start\").to(\"mock:foo\").to(\"mock:bar\").to(\"mock:result\")";
        String expectedDSL = "intercept().choice().when(body().contains(\"Hello\")).to(\"mock:intercepted\").end();from(\"direct:start\").to(\"mock:foo\").to(\"mock:bar\").to(\"mock:result\")";

        assertEquals(expectedDSL, render(DSL));
    }

    public void testInterceptWhenStop() throws Exception {
        String DSL = "intercept().when(body().contains(\"Hello\")).to(\"mock:intercepted\").stop();from(\"direct:start\").to(\"mock:foo\").to(\"mock:bar\").to(\"mock:result\")";
        String expectedDSL = "intercept().choice().when(body().contains(\"Hello\")).to(\"mock:intercepted\").stop().end();from(\"direct:start\").to(\"mock:foo\").to(\"mock:bar\").to(\"mock:result\")";

        assertEquals(expectedDSL, render(DSL));
    }

}
