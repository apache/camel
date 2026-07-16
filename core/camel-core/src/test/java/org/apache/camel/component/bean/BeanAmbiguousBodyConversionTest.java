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
package org.apache.camel.component.bean;

import java.util.ArrayList;
import java.util.Collection;

import org.apache.camel.CamelExecutionException;
import org.apache.camel.ContextTestSupport;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BeanAmbiguousBodyConversionTest extends ContextTestSupport {

    @Test
    public void testAmbiguousExceptionListsDistinctCandidates() {
        CamelExecutionException e = assertThrows(CamelExecutionException.class,
                () -> template.sendBody("direct:start", "123"));

        AmbiguousMethodCallException cause = assertInstanceOf(AmbiguousMethodCallException.class, e.getCause());
        Collection<MethodInfo> methods = cause.getMethods();

        assertEquals(2, methods.size(), "Should list exactly two distinct candidates");
        ArrayList<MethodInfo> list = new ArrayList<>(methods);
        assertTrue(list.get(0) != list.get(1),
                "The two candidates should be different method instances");
    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .bean(MyAmbiguousBean.class)
                        .to("mock:result");
            }
        };
    }

    @SuppressWarnings("unused")
    public static class MyAmbiguousBean {
        public String foo(Integer num) {
            return "foo:" + num;
        }

        public String bar(Double num) {
            return "bar:" + num;
        }
    }
}
