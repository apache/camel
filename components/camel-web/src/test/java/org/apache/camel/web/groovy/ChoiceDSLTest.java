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
 * a test case for choice DSL
 */
public class ChoiceDSLTest extends GroovyRendererTestSupport {

    public void testChoiceWithMethod() throws Exception {
        String dsl = "from(\"direct:start\").choice()"
            + ".when().method(\"controlBean\", \"isDetour\").to(\"mock:detour\")"
            + ".end()"
            + ".to(\"mock:result\")";
        String expectedDSL = dsl;

        assertEquals(expectedDSL, render(dsl));
    }

    public void testChoiceWithPredication() throws Exception {
        String dsl = "from(\"direct:start\").choice()"
            + ".when(header(\"username\").isNull()).to(\"mock:god\")"
            + ".when(header(\"admin\").isEqualTo(\"true\")).to(\"mock:admin\")"
            + ".otherwise().to(\"mock:guest\")"
            + ".end()";
        String expectedDSL = dsl;

        assertEquals(expectedDSL, render(dsl));
    }

    public void testChoiceWithoutEnd() throws Exception {
        String dsl = "from(\"direct:start\").split().body().choice()"
            + ".when().method(\"orderItemHelper\", \"isWidget\").to(\"bean:widgetInventory\", \"seda:aggregate\")"
            + ".otherwise().to(\"bean:gadgetInventory\", \"seda:aggregate\")";
        
        //TODO check this result
        String expectedDSL = "from(\"direct:start\").split(body()).choice()"
            + ".when().method(\"orderItemHelper\", \"isWidget\").to(\"bean:widgetInventory\").to(\"seda:aggregate\")"
            + ".otherwise().to(\"bean:gadgetInventory\").to(\"seda:aggregate\")"
            + ".end()";

        assertEquals(expectedDSL, render(dsl));
    }
}
