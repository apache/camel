/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.groovy;

import groovy.lang.GroovyClassLoader;
import groovy.lang.MetaClassRegistry;
import groovy.lang.MetaClass;
import groovy.lang.Closure;
import groovy.lang.ProxyMetaClass;
import org.apache.camel.CamelContext;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.model.ProcessorType;

/**
 * @version $Revision: 1.1 $
 */
public class GroovyTest extends ContextTestSupport {
    protected String expected = "<hello>world!</hello>";
    protected String groovyBuilderClass = "org.apache.camel.groovy.example.GroovyRoutes";

    public void testSendMatchingMessage() throws Exception {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:results");
        resultEndpoint.expectedBodiesReceived(expected);

        template.sendBodyAndHeader("direct:a", expected, "foo", "bar");

        assertMockEndpointsSatisifed();
    }

    public void testSendNotMatchingMessage() throws Exception {
        MockEndpoint resultEndpoint = getMockEndpoint("mock:results");
        resultEndpoint.expectedMessageCount(0);

        template.sendBodyAndHeader("direct:a", expected, "foo", "123");

        assertMockEndpointsSatisifed();
    }


    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext answer = super.createCamelContext();

/*
        MetaClassRegistry metaClassRegistry = MetaClassRegistry.getInstance(MetaClassRegistry.LOAD_DEFAULT);
        MetaClass metaClass = metaClassRegistry.getMetaClass(ProcessorType.class);
        metaClass = new ProxyMetaClass(metaClassRegistry, ProcessorType.class, metaClass);
        metaClass.addNewInstanceMethod(CamelGroovyMethods.class.getMethod("filter", ProcessorType.class, Closure.class));
        metaClassRegistry.setMetaClass(ProcessorType.class, metaClass);
*/

        GroovyClassLoader classLoader = new GroovyClassLoader();
        Class<?> type = classLoader.loadClass(groovyBuilderClass);
        Object object = answer.getInjector().newInstance(type);
        RouteBuilder builder = assertIsInstanceOf(RouteBuilder.class, object);

        log.info("Loaded builder: " + builder);
        answer.addRoutes(builder);

        return answer;
    }
}