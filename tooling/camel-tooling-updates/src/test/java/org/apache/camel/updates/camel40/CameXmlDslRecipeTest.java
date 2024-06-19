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
package org.apache.camel.updates.camel40;

import org.apache.camel.updates.CamelTestUtil;
import org.junit.jupiter.api.Test;
import org.openrewrite.java.JavaParser;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.xml.Assertions.xml;

public class CameXmlDslRecipeTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        CamelTestUtil.recipe(spec, CamelTestUtil.CamelVersion.v4_0)
                .parser(JavaParser.fromJavaVersion().logCompilationWarningsAndErrors(true))
                .typeValidationOptions(TypeValidation.none());
    }

    @Test
    void testDescription() {
        //language=xml
        rewriteRun(xml("""
                <routes xmlns="http://camel.apache.org/schema/spring">
                    <route id="myRoute">
                        <description>Something that this route do</description>
                        <from uri="kafka:cheese"/>
                        <setBody>
                           <constant>Hello Camel K!</constant>
                        </setBody>
                       <to uri="log:info"/>
                    </route>
                    <route id="myRoute2">
                        <description>Something that this route2 do</description>
                        <from uri="kafka:cheese"/>
                        <setBody>
                           <constant>Hello Camel K!</constant>
                        </setBody>
                       <to uri="log:info"/>
                    </route>
                </routes>
                                                """, """
                    <routes xmlns="http://camel.apache.org/schema/spring">
                        <route id="myRoute" description="Something that this route do">
                            <from uri="kafka:cheese"/>
                            <setBody>
                               <constant>Hello Camel K!</constant>
                            </setBody>
                           <to uri="log:info"/>
                        </route>
                        <route id="myRoute2" description="Something that this route2 do">
                            <from uri="kafka:cheese"/>
                            <setBody>
                               <constant>Hello Camel K!</constant>
                            </setBody>
                           <to uri="log:info"/>
                        </route>
                    </routes>
                """));
    }

    @Test
    void testCircuitBreakerFull() {
        //language=xml
        rewriteRun(xml("""
                <differentContext>
                    <circuitBreaker>
                        <resilience4jConfiguration>
                            <bulkheadEnabled>5643</bulkheadEnabled>
                            <bulkheadMaxConcurrentCalls>aaaa</bulkheadMaxConcurrentCalls>
                            <bulkheadMaxWaitDuration>1</bulkheadMaxWaitDuration>
                            <timeoutEnabled>true</timeoutEnabled>
                            <timeoutExecutorService>1</timeoutExecutorService>
                            <timeoutDuration>1</timeoutDuration>
                            <timeoutCancelRunningFuture></timeoutCancelRunningFuture>
                        </resilience4jConfiguration>
                    </circuitBreaker>
                </differentContext>
                                                """,
                """
                        <differentContext>
                            <circuitBreaker>
                                <resilience4jConfiguration bulkheadEnabled="5643" bulkheadMaxConcurrentCalls="aaaa" bulkheadMaxWaitDuration="1" timeoutEnabled="true" timeoutExecutorService="1" timeoutDuration="1">
                                </resilience4jConfiguration>
                            </circuitBreaker>
                        </differentContext>
                            """));
    }

    @Test
    void testCircuitBreaker() {
        //language=xml
        rewriteRun(xml("""
                <route>
                    <from uri="direct:start"/>
                    <circuitBreaker>
                        <resilience4jConfiguration>
                            <timeoutEnabled>true</timeoutEnabled>
                            <timeoutDuration>2000</timeoutDuration>
                        </resilience4jConfiguration>
                    </circuitBreaker>
                    <to uri="mock:result"/>
                </route>
                                                """, """
                <route>
                    <from uri="direct:start"/>
                    <circuitBreaker>
                        <resilience4jConfiguration timeoutEnabled="true" timeoutDuration="2000">
                        </resilience4jConfiguration>
                    </circuitBreaker>
                    <to uri="mock:result"/>
                </route>
                """));
    }
}
