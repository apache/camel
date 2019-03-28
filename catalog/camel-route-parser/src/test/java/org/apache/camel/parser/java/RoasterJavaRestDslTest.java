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
package org.apache.camel.parser.java;

import java.io.File;
import java.util.List;

import org.apache.camel.parser.RestDslParser;
import org.apache.camel.parser.model.RestConfigurationDetails;
import org.apache.camel.parser.model.RestServiceDetails;
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
    public void parseRestConfiguration() throws Exception {
        JavaClassSource clazz = (JavaClassSource) Roaster.parse(new File("src/test/java/org/apache/camel/parser/java/MyRestDslRouteBuilder.java"));

        List<RestConfigurationDetails> list = RestDslParser.parseRestConfiguration(clazz, ".",
            "src/test/java/org/apache/camel/parser/java/MyRestDslRouteBuilder.java", true);
        assertEquals(1, list.size());
        RestConfigurationDetails details = list.get(0);
        assertEquals("27", details.getLineNumber());
        assertEquals("41", details.getLineNumberEnd());
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

        assertEquals(1, details.getComponentProperties().size());
        assertEquals("123", details.getComponentProperties().get("foo"));
        assertEquals(1, details.getEndpointProperties().size());
        assertEquals("false", details.getEndpointProperties().get("pretty"));
        assertEquals(1, details.getEndpointProperties().size());
        assertEquals("456", details.getConsumerProperties().get("bar"));
        assertEquals(2, details.getCorsHeaders().size());
        assertEquals("value1", details.getCorsHeaders().get("key1"));
        assertEquals("value2", details.getCorsHeaders().get("key2"));
    }

    @Test
    public void parseRestService() throws Exception {
        JavaClassSource clazz = (JavaClassSource) Roaster.parse(new File("src/test/java/org/apache/camel/parser/java/MyRestDslRouteBuilder.java"));

        List<RestServiceDetails> list = RestDslParser.parseRestService(clazz, ".",
            "src/test/java/org/apache/camel/parser/java/MyRestDslRouteBuilder.java", true);
        assertEquals(1, list.size());
        RestServiceDetails details = list.get(0);
        assertEquals("43", details.getLineNumber());
        assertEquals("49", details.getLineNumberEnd());
        assertEquals("src/test/java/org/apache/camel/parser/java/MyRestDslRouteBuilder.java", details.getFileName());
        assertEquals("configure", details.getMethodName());
        assertEquals("org.apache.camel.parser.java.MyRestDslRouteBuilder", details.getClassName());

        assertEquals("/foo", details.getPath());
        assertEquals("my foo service", details.getDescription());
        assertEquals("json", details.getProduces());
        assertEquals("json", details.getProduces());
        assertEquals(2, details.getVerbs().size());
        assertEquals("get", details.getVerbs().get(0).getMethod());
        assertEquals("{id}", details.getVerbs().get(0).getUri());
        assertEquals("get by id", details.getVerbs().get(0).getDescription());
        assertEquals("log:id", details.getVerbs().get(0).getTo());
        assertEquals("false", details.getVerbs().get(0).getApiDocs());
        assertEquals("post", details.getVerbs().get(1).getMethod());
        assertEquals("post something", details.getVerbs().get(1).getDescription());
        assertEquals("xml", details.getVerbs().get(1).getBindingMode());
        assertEquals("log:post", details.getVerbs().get(1).getToD());
        assertNull(details.getVerbs().get(1).getUri());
    }

}
