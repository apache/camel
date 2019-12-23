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
package org.apache.camel.language;

import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.ExpressionIllegalSyntaxException;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.bean.MethodNotFoundException;
import org.junit.Test;

public class BeanLanguageInvalidOGNLTest extends ContextTestSupport {

    @Test
    public void testBeanLanguageInvalidOGNL() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("direct:start").transform().method(MyReallyCoolBean.class, "getOther[xx");
            }
        });
        try {
            context.start();
            fail("Should have thrown exception");
        } catch (Exception e) {
            RuntimeCamelException rce = assertIsInstanceOf(RuntimeCamelException.class, e);
            MethodNotFoundException mnfe = assertIsInstanceOf(MethodNotFoundException.class, rce.getCause());
            assertEquals("getOther[xx", mnfe.getMethodName());
            ExpressionIllegalSyntaxException cause = assertIsInstanceOf(ExpressionIllegalSyntaxException.class, mnfe.getCause());
            assertEquals("Illegal syntax: getOther[xx", cause.getMessage());
            assertEquals("getOther[xx", cause.getExpression());
        }
    }

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    public static class MyReallyCoolBean {

        private Map<?, ?> map = new LinkedHashMap<>();

        public Map<?, ?> getOther() {
            return map;
        }

    }

}
