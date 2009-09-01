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

import org.junit.Test;

/**
 * a test case for filter DSL: from().filter().to()
 */
public class FilterDSLTest extends GroovyRendererTestSupport {

    @Test
    public void testFilterHeader() throws Exception {
        String dsl = "from(\"direct:start\").filter(header(\"foo\").isEqualTo(\"bar\")).to(\"mock:result\")";
        assertEquals(dsl, render(dsl));
    }

    @Test
    public void testFilterBody() throws Exception {
        String dsl = "from(\"direct:start\").filter(body().contains(\"World\")).to(\"mock:result\")";
        assertEquals(dsl, render(dsl));
    }

    @Test
    public void testFilterMethod() throws Exception {
        String dsl = "from(\"direct:start\").filter().method(\"myBean\", \"matches\").to(\"mock:result\")";
        assertEquals(dsl, render(dsl));
    }

    @Test
    public void testFilterXPath() throws Exception {
        String dsl = "from(\"direct:start\").filter().xpath(\"/person[@Name='James']\").to(\"mock:result\")";
        assertEquals(dsl, render(dsl));
    }
}
