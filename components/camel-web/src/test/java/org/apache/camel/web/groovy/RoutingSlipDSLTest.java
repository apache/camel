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
 * a test case for routingSlip DSL
 */
public class RoutingSlipDSLTest extends GroovyRendererTestSupport {

    public void testRoutingSlip() throws Exception {
        String DSL = "from(\"direct:a\").routingSlip(\"myHeader\").to(\"mock:end\")";
        String expectedDSL = "from(\"direct:a\").routingSlip(\"myHeader\", \",\").to(\"mock:end\")";

        assertEquals(expectedDSL, render(DSL));
    }

    public void testRoutingSlip1() throws Exception {
        String DSL = "from(\"direct:b\").routingSlip(\"aRoutingSlipHeader\")";
        String expectedDSL = "from(\"direct:b\").routingSlip(\"aRoutingSlipHeader\", \",\")";

        assertEquals(expectedDSL, render(DSL));
    }

    public void testRoutingSlip2() throws Exception {
        String DSL = "from(\"direct:c\").routingSlip(\"aRoutingSlipHeader\", \"#\")";
        String expectedDSL = DSL;

        assertEquals(expectedDSL, render(DSL));
    }

}
