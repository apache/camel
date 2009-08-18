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

import java.util.HashMap;
import java.util.Map;

/**
 * 
 */
public class PoolEnrichDSLTest extends GroovyRendererTestSupport {

    /**
     * a route involving a external class: aggregationStrategy
     * 
     * @throws Exception
     * TODO: fix this test!
     */
    public void fixmeTestPollEnrich() throws Exception {
        String dsl = "from(\"direct:start\").pollEnrich(\"direct:foo\", 1000, aggregationStrategy).to(\"mock:result\")";
        String[] importClasses = new String[] {"import org.apache.camel.processor.enricher.*"};
        Map<String, String> newObjects = new HashMap<String, String>();
        newObjects.put("aggregationStrategy", "SampleAggregator");

        assertEquals(dsl, render(dsl, importClasses, newObjects));
    }

    public void testPollEnrichWithoutAggregationStrategy() throws Exception {
        String dsl = "from(\"direct:start\").pollEnrich(\"direct:foo\", 1000).to(\"mock:result\")";
        assertEquals(dsl, render(dsl));
    }
}
