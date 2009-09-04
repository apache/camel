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

import org.apache.camel.ExchangePattern;
import org.junit.Ignore;
import org.junit.Test;

/**
 * 
 */
public class SetExchangePatternDSLTest extends GroovyRendererTestSupport {

    @Test
    public void testInOnly() throws Exception {
        String dsl = "from(\"direct:start\").inOnly(\"mock:result\")";
        assertEquals(dsl, render(dsl));
    }

    @Test
    public void testInOut() throws Exception {
        String dsl = "from(\"direct:start\").inOut(\"mock:result\")";
        assertEquals(dsl, render(dsl));
    }

    /**
     * to(ExchangePattern.PATTERN, dest) -> PATTERN(dest) 
     * since we can't distinguish them at CamelContext
     */
    @Test
    public void testToWithExchangePattern() throws Exception {
        String dsl = "from(\"direct:start\").to(ExchangePattern.InOnly, \"mock:result\")";
        String expected = "from(\"direct:start\").inOnly(\"mock:result\")";
        assertEquals(expected, render(dsl));
    }

    @Test
    public void testSetExchangepattern() throws Exception {
        String dsl = "from(\"direct:start\").setExchangePattern(ExchangePattern.InOnly).to(\"mock:result\")";
        assertEquals(dsl, render(dsl));
    }
}
