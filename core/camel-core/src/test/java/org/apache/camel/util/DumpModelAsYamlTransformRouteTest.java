/*
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
package org.apache.camel.util;

import java.nio.file.Paths;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.support.PluginHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class DumpModelAsYamlTransformRouteTest extends DumpModelAsYamlTestSupport {

    @Test
    public void testDumpModelAsYaml() throws Exception {
        String out = PluginHelper.getModelToYAMLDumper(context).dumpModelAsYaml(context, context.getRouteDefinition("myRoute"));
        assertNotNull(out);
        log.info(out);

        String expected
                = IOHelper.stripLineComments(Paths.get("src/test/resources/org/apache/camel/util/transform.yaml"), "#", true);
        Assertions.assertEquals(expected, out);
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").routeId("myRoute").transform().simple("Hello ${body}").to("mock:result").id("myMock");
            }
        };
    }
}
