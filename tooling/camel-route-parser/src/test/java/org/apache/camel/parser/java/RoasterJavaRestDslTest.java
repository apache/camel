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
package org.apache.camel.parser.java;

import java.io.File;
import java.util.List;

import org.apache.camel.parser.RestDslParser;
import org.apache.camel.parser.model.RestConfigurationDetails;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.jboss.forge.roaster.Roaster;
import org.jboss.forge.roaster.model.source.JavaClassSource;
import org.junit.Test;

public class RoasterJavaRestDslTest extends CamelTestSupport {

    @Override
    public boolean isDumpRouteCoverage() {
        return false;
    }

    @Test
    public void parseTree() throws Exception {
        JavaClassSource clazz = (JavaClassSource) Roaster.parse(new File("src/test/java/org/apache/camel/parser/java/MyRestDslRouteBuilder.java"));

        List<RestConfigurationDetails> list = RestDslParser.parseRestConfiguration(clazz, ".",
            "src/test/java/org/apache/camel/parser/java/MyRestDslRouteBuilder.java", true);
        assertEquals(1, list.size());
        RestConfigurationDetails details = list.get(0);
        assertEquals("27", details.getLineNumber());
        assertEquals("36", details.getLineNumberEnd());
        assertEquals("src/test/java/org/apache/camel/parser/java/MyRestDslRouteBuilder.java", details.getFileName());
        assertEquals("configure", details.getMethodName());
        assertEquals("org.apache.camel.parser.java.MyRestDslRouteBuilder", details.getClassName());
        assertEquals("1234", details.getPort());
        assertEquals("myapi", details.getContextPath());
        assertEquals("jetty", details.getComponent());
        assertEquals("json", details.getBindingMode());
        assertEquals("swagger", details.getApiComponent());
        assertEquals("myapi/swagger", details.getApiContextPath());
        assertEquals("localhost", details.getApiHost());
        assertEquals("true", details.getSkipBindingOnErrorCode());
        assertEquals("https", details.getScheme());
        assertEquals("allLocalIp", details.getHostNameResolver());
    }

}
