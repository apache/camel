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
package org.apache.camel.component.bean;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

import javax.naming.Context;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Header;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.util.jndi.JndiContext;

/**
 * Test inheritance of parameter binding annotations from superclasses and
 * interfaces.
 */
public class BeanWithAnnotationInheritedTest extends ContextTestSupport {

    public void testWithAnnotationsFromOneInterface() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("x1y1");
        template.requestBody("direct:in1", "whatever");
        mock.assertIsSatisfied();
    }

    public void testWithAnnotationsFromTwoInterfaces() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("x2y2");
        template.requestBody("direct:in2", "whatever");
        mock.assertIsSatisfied();
    }

    public void testWithAnnotationsFromSuperclassAndInterface() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("x3y3");
        template.requestBody("direct:in3", "whatever");
        mock.assertIsSatisfied();
    }

    public void testWithAnnotationsFromImplementationClassAndInterface() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("x4y4");
        template.requestBody("direct:in4", "whatever");
        mock.assertIsSatisfied();
    }

    public void testWithAnnotationsFromOneInterfaceInheritedByProxy() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("x5y5");
        template.requestBody("direct:in5", "whatever");
        mock.assertIsSatisfied();
    }

    protected Context createJndiContext() throws Exception {
        JndiContext answer = new JndiContext();
        answer.bind("b", new B());
        answer.bind("p", Proxy.newProxyInstance(I1.class.getClassLoader(), new Class[]{I1.class}, new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                if (method.getName().equals("m1")) {
                    return args[0].toString() + args[1].toString();
                } else {
                    return null;
                }
            }
        }));
        return answer;
    }

    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            public void configure() throws Exception {
                from("direct:in1")
                    .setHeader("foo", constant("x1"))
                    .setHeader("bar", constant("y1"))
                    .to("bean:b?method=m1")
                    .to("mock:result");
                from("direct:in2")
                    .setHeader("foo", constant("x2"))
                    .setHeader("bar", constant("y2"))
                    .to("bean:b?method=m2")
                    .to("mock:result");
                from("direct:in3")
                    .setHeader("foo", constant("x3"))
                    .setHeader("bar", constant("y3"))
                    .to("bean:b?method=m3")
                    .to("mock:result");
                from("direct:in4")
                    .setHeader("foo", constant("x4"))
                    .setHeader("bar", constant("y4"))
                    .to("bean:b?method=m4")
                    .to("mock:result");
                from("direct:in5")
                    .setHeader("foo", constant("x5"))
                    .setHeader("bar", constant("y5"))
                    .to("bean:p?method=m1")
                    .to("mock:result");
            }
        };
    }

    private interface I1 {
        String m1(@Header("foo")String h1, @Header("bar")String h2);
        String m2(@Header("foo")String h1, String h2);
    }

    private interface I2 {
        String m2(String h1, @Header("bar")String h2);
        String m3(@Header("foo")String h1, String h2);
        String m4(@Header("foo")String h1, String h2);
    }

    private abstract static class A implements I2 {
        public String m3(String h1, @Header("bar")String h2) {
            return h1 + h2;
        }
    }

    private static class B extends A implements I1 {
        public String m1(String h1, String h2) {
            return h1 + h2;
        }
        public String m2(String h1, String h2) {
            return h1 + h2;
        }
        public String m4(String h1, @Header("bar")String h2) {
            return h1 + h2;
        }
    }
}
