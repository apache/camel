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
package org.apache.camel.updates.camel44;

import org.apache.camel.updates.CamelTestUtil;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.openrewrite.properties.Assertions;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

public class CamelUpdate44Test implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        CamelTestUtil.recipe(spec, CamelTestUtil.CamelVersion.v4_4)
                .parser(CamelTestUtil.parserFromClasspath(CamelTestUtil.CamelVersion.v4_0,
                        "camel-api", "camel-util", "camel-base", "camel-core-model", "camel-json-validator"))
                .typeValidationOptions(TypeValidation.none());
    }

    /**
     * <a href="https://camel.apache.org/manual/camel-4x-upgrade-guide-4_4.html#_camel_main">doc</a>
     */
    @Test
    void testCamelMainRouteControllerProperty() {
        rewriteRun(Assertions.properties("""
                   #test
                   camel.main.routeControllerBackOffDelay=1000
                   camel.main.routeControllerSuperviseEnabled = true
                """,
                """
                            #test
                            camel.routeController.backOffDelay=1000
                            camel.routeController.enabled = true
                        """));
    }

    /**
     * <a href="https://camel.apache.org/manual/camel-4x-upgrade-guide-4_4.html#_camel_main">doc</a>
     */
    @Test
    void testCamelMainRouteControllerDashedProperty() {
        rewriteRun(Assertions.properties("""
                   #test
                   camel.main.route-controller-back-off-max-attempts = 10
                   camel.main.route-controller-supervise-enabled = true
                """,
                """
                            #test
                            camel.routeController.back-off-max-attempts = 10
                            camel.routeController.enabled = true
                        """));
    }

    /**
     * <a href="https://camel.apache.org/manual/camel-4x-upgrade-guide-4_4.html#_camel_json_validator">doc</a>
     */
    @Test
    void testJsonValidator() {
        //language=java
        rewriteRun(java(
                """
                            import org.apache.camel.component.jsonvalidator.DefaultJsonSchemaLoader;

                            public class CustomJsonValidator extends DefaultJsonSchemaLoader {
                            }
                        """,
                """
                            import org.apache.camel.component.jsonvalidator.DefaultJsonUriSchemaLoader;

                            public class CustomJsonValidator extends DefaultJsonUriSchemaLoader {
                            }
                        """));
    }

    /**
     * <a href="https://camel.apache.org/manual/camel-4x-upgrade-guide-4_4.html#_camel_core">doc</a>
     */
    @Test
    void testStopWatchConstructor() {
        //language=java
        rewriteRun(java("""
                    import org.apache.camel.util.StopWatch;

                    public class StopWatchTest {

                        public void test() {
                           StopWatch sw = new StopWatch(Long.parseLong("1"));
                        }
                    }
                """,
                """
                        import org.apache.camel.util.StopWatch;

                        public class StopWatchTest {

                            public void test() {
                               StopWatch sw = /*Removed the deprecated constructor from the internal class org.apache.camel.util.StopWatch.
                        Users of this class are advised to use the default constructor if necessary.Changed exception thrown from IOException to Exception.
                        */new StopWatch();
                            }
                        }
                        """));
    }

    /**
     * <a href="https://camel.apache.org/manual/camel-4x-upgrade-guide-4_4.html#_camel_core">doc</a>
     */
    @Test
    void testExchangeGetCreated() {
        //language=java
        rewriteRun(java("""
                    import org.apache.camel.Exchange;

                    public class ExchangeTest {

                        public void test() {
                            Exchange ex = null;
                            ex.getCreated();
                        }
                    }
                """,
                """
                        import org.apache.camel.Exchange;

                        public class ExchangeTest {

                            public void test() {
                                Exchange ex = null;
                                ex.getClock().getCreated();
                            }
                        }
                        """));
    }

    /**
     * <a href="https://camel.apache.org/manual/camel-4x-upgrade-guide-4_4.html#_camel_core">doc</a>
     */
    @Test
    void testPropertiesLookup() {
        //language=java
        rewriteRun(java("""
                    import org.apache.camel.component.properties.PropertiesLookup;

                    public class PropertiesLookupTest  {

                        public void test() throws Exception {
                            PropertiesLookup pl = null;

                            pl.lookup("test");
                        }
                    }
                """,
                """
                        import org.apache.camel.component.properties.PropertiesLookup;

                        public class PropertiesLookupTest  {

                            public void test() throws Exception {
                                PropertiesLookup pl = null;

                                pl.lookup("test", null);
                            }
                        }
                        """));
    }

    /**
     * <a href="https://camel.apache.org/manual/camel-4x-upgrade-guide-4_4.html#_camel_core">doc</a>
     */
    @Test
    void testJsonpath1() {
        //language=java
        rewriteRun(java("""
                    import org.apache.camel.builder.RouteBuilder;

                    public class Jsonpath1Test extends RouteBuilder {
                        @Override
                        public void configure()  {
                            from("direct:in").choice().when().jsonpath("something", true, Object.class, "header")
                                    .to("mock:premium");
                        }
                    }
                """,
                """
                        import org.apache.camel.builder.RouteBuilder;

                        public class Jsonpath1Test extends RouteBuilder {
                            @Override
                            public void configure()  {
                                /*Some Java DSL for tokenize, xmlTokenize, xpath, xquery and jsonpath has been removed as part of making the DSL model consistent.
                        See https://camel.apache.org/manual/camel-4x-upgrade-guide-4_4.html#_camel_core for more details.
                        */from("direct:in").choice().when().removed_jsonpath("something", true, Object.class, "header")
                                        .to("mock:premium");
                            }
                        }
                        """));
    }

    /**
     * <a href="https://camel.apache.org/manual/camel-4x-upgrade-guide-4_4.html#_camel_core">doc</a>
     */
    @Test
    void testJsonpath2() {
        //language=java
        rewriteRun(java("""
                    import org.apache.camel.builder.RouteBuilder;

                    public class Jsonpath2Test extends RouteBuilder {
                        @Override
                        public void configure()  {
                            from("direct:in").choice().when().jsonpathWriteAsString("something", true, "header")
                                    .to("mock:premium");
                        }
                    }
                """,
                """
                        import org.apache.camel.builder.RouteBuilder;

                        public class Jsonpath2Test extends RouteBuilder {
                            @Override
                            public void configure()  {
                                /*Some Java DSL for tokenize, xmlTokenize, xpath, xquery and jsonpath has been removed as part of making the DSL model consistent.
                        See https://camel.apache.org/manual/camel-4x-upgrade-guide-4_4.html#_camel_core for more details.
                        */from("direct:in").choice().when().removed_jsonpathWriteAsString("something", true, "header")
                                        .to("mock:premium");
                            }
                        }
                        """));
    }

    /**
     * Removed tokenize(String token, boolean regex, int group, String groupDelimiter, boolean skipFirst)
     */
    @Test
    void testTokenize1() {
        //language=java
        rewriteRun(java("""
                    import org.apache.camel.builder.RouteBuilder;

                    public class Tokenize1Test extends RouteBuilder {
                        @Override
                        public void configure()  {
                            from("direct:in").choice().when().tokenize("token", true, 0, "groupDelimiter", true)
                                    .to("mock:premium");
                        }
                    }
                """,
                """
                        import org.apache.camel.builder.RouteBuilder;

                        public class Tokenize1Test extends RouteBuilder {
                            @Override
                            public void configure()  {
                                /*Some Java DSL for tokenize, xmlTokenize, xpath, xquery and jsonpath has been removed as part of making the DSL model consistent.
                        See https://camel.apache.org/manual/camel-4x-upgrade-guide-4_4.html#_camel_core for more details.
                        */from("direct:in").choice().when().removed_tokenize("token", true, 0, "groupDelimiter", true)
                                        .to("mock:premium");
                            }
                        }
                        """));
    }

    /**
     * Removed tokenize(String token, String headerName)
     */
    @Test
    void testTokenize2() {
        //language=java
        rewriteRun(java("""
                    import org.apache.camel.builder.RouteBuilder;

                    public class Tokenize2Test extends RouteBuilder {
                        @Override
                        public void configure()  {
                            from("direct:in").choice().when().tokenize("token", "header")
                                    .to("mock:premium");
                        }
                    }
                """,
                """
                        import org.apache.camel.builder.RouteBuilder;

                        public class Tokenize2Test extends RouteBuilder {
                            @Override
                            public void configure()  {
                                /*Some Java DSL for tokenize, xmlTokenize, xpath, xquery and jsonpath has been removed as part of making the DSL model consistent.
                        See https://camel.apache.org/manual/camel-4x-upgrade-guide-4_4.html#_camel_core for more details.
                        */from("direct:in").choice().when().removed_tokenize("token", "header")
                                        .to("mock:premium");
                            }
                        }
                        """));
    }

    /**
     * Removed tokenize(String token, String headerName, boolean regex)
     */
    @Test
    void testTokenize3() {
        //language=java
        rewriteRun(java("""
                    import org.apache.camel.builder.RouteBuilder;

                    public class Tokenize3Test extends RouteBuilder {
                        @Override
                        public void configure()  {
                            from("direct:in").choice().when().tokenize("token", "header", true)
                                    .to("mock:premium");
                        }
                    }
                """,
                """
                        import org.apache.camel.builder.RouteBuilder;

                        public class Tokenize3Test extends RouteBuilder {
                            @Override
                            public void configure()  {
                                /*Some Java DSL for tokenize, xmlTokenize, xpath, xquery and jsonpath has been removed as part of making the DSL model consistent.
                        See https://camel.apache.org/manual/camel-4x-upgrade-guide-4_4.html#_camel_core for more details.
                        */from("direct:in").choice().when().removed_tokenize("token", "header", true)
                                        .to("mock:premium");
                            }
                        }
                        """));
    }

    /**
     * Removed xpath(String text, String headerName)
     */
    @Test
    @Disabled //https://github.com/quarkusio/quarkus-updates/issues/142
    void testXpath1() {
        //language=java
        rewriteRun(java("""
                import org.apache.camel.builder.RouteBuilder;

                public class Xpath1Test extends RouteBuilder {
                    @Override
                    public void configure()  {
                            from("direct:in").choice().when().xpath("/invoice/@orderType = 'premium'", "invoiceDetails")
                                .to("mock:premium");
                    }
                }
                """,
                """
                        import org.apache.camel.builder.RouteBuilder;

                        public class Xpath1Test extends RouteBuilder {
                            @Override
                            public void configure()  {
                                    /*Some Java DSL for tokenize, xmlTokenize, xpath, xquery and jsonpath has been removed as part of making the DSL model consistent.
                        See https://camel.apache.org/manual/camel-4x-upgrade-guide-4_4.html#_camel_core for more details.
                        */from("direct:in").choice().when().removed_xpath("/invoice/@orderType = 'premium'", "invoiceDetails")
                                        .to("mock:premium");
                            }
                        }
                            """));
    }

    /**
     * Removed xpath(String text, Class<?> resultType, String headerName)
     */
    @Test
    @Disabled //https://github.com/quarkusio/quarkus-updates/issues/142
    void testXpath2() {
        //language=java
        rewriteRun(java("""
                    import org.apache.camel.builder.RouteBuilder;

                    public class Xpath2Test extends RouteBuilder {
                        @Override
                        public void configure()  {
                            from("direct:in").choice().when().xpath("text", Object.class, "header")
                                    .to("mock:premium");
                        }
                    }
                """,
                """
                        import org.apache.camel.builder.RouteBuilder;

                        public class Xpath2Test extends RouteBuilder {
                            @Override
                            public void configure()  {
                                /*Some Java DSL for tokenize, xmlTokenize, xpath, xquery and jsonpath has been removed as part of making the DSL model consistent.
                        See https://camel.apache.org/manual/camel-4x-upgrade-guide-4_4.html#_camel_core for more details.
                        */from("direct:in").choice().when().removed_xpath("text", Object.class, "header")
                                        .to("mock:premium");
                            }
                        }
                        """));
    }

    /**
     * Removed xpath(String text, Class<?> resultType, Namespaces namespaces, String headerName) {
     */
    @Test
    void testXpath3() {
        //language=java
        rewriteRun(java("""
                    import org.apache.camel.builder.RouteBuilder;

                    public class Xpath3Test extends RouteBuilder {
                        @Override
                        public void configure()  {
                            from("direct:in").choice().when().xpath("text", Object.class, null, "header")
                                    .to("mock:premium");
                        }
                    }
                """,
                """
                        import org.apache.camel.builder.RouteBuilder;

                        public class Xpath3Test extends RouteBuilder {
                            @Override
                            public void configure()  {
                                /*Some Java DSL for tokenize, xmlTokenize, xpath, xquery and jsonpath has been removed as part of making the DSL model consistent.
                        See https://camel.apache.org/manual/camel-4x-upgrade-guide-4_4.html#_camel_core for more details.
                        */from("direct:in").choice().when().removed_xpath("text", Object.class, null, "header")
                                        .to("mock:premium");
                            }
                        }
                        """));
    }

    /**
     * Removed xquery(String text, String headerName)
     */
    @Test
    @Disabled //https://github.com/quarkusio/quarkus-updates/issues/142
    void testXquery1() {
        //language=java
        rewriteRun(java("""
                    import org.apache.camel.builder.RouteBuilder;

                    public class Xquery1Test extends RouteBuilder {
                        @Override
                        public void configure()  {
                            from("direct:in").choice().when().xquery("text", "header")
                                    .to("mock:premium");
                        }
                    }
                """,
                """
                        import org.apache.camel.builder.RouteBuilder;

                        public class Xquery1Test extends RouteBuilder {
                            @Override
                            public void configure()  {
                                /*Some of the Java DSL for tokenize, xmlTokenize, xpath, xquery and jsonpath has been removed as part of making the DSL model consistent.
                        See https://camel.apache.org/manual/camel-4x-upgrade-guide-4_4.html#_camel_core for more details.*/from("direct:in").choice().when().xquery("text", "header")
                                        .to("mock:premium");
                            }
                        }
                        """));
    }

    /**
     * Removed xquery(String text, Class<?> resultType, String headerName)
     */
    @Test
    @Disabled //https://github.com/quarkusio/quarkus-updates/issues/142
    void testXquery2() {
        //language=java
        rewriteRun(java("""
                    import org.apache.camel.builder.RouteBuilder;

                    public class Tokenize1Test extends RouteBuilder {
                        @Override
                        public void configure()  {
                            from("direct:in").choice().when().xquery("text", Object.class, "header")
                                    .to("mock:premium");
                        }
                    }
                """,
                """
                        import org.apache.camel.builder.RouteBuilder;

                        public class Jsonpath2Test extends RouteBuilder {
                            @Override
                            public void configure()  {
                                /*Some of the Java DSL for tokenize, xmlTokenize, xpath, xquery and jsonpath has been removed as part of making the DSL model consistent.
                        See https://camel.apache.org/manual/camel-4x-upgrade-guide-4_4.html#_camel_core for more details.*/from("direct:in").choice().when().jsonpathWriteAsString("something", true, "header")
                                        .to("mock:premium");
                            }
                        }
                        """));
    }

    /**
     * Removed xquery(String text, Class<?> resultType, Namespaces namespaces, String headerName) {
     */
    @Test
    @Disabled //https://github.com/quarkusio/quarkus-updates/issues/142
    void testXquery3() {
        //language=java
        rewriteRun(java("""
                    import org.apache.camel.builder.RouteBuilder;

                    public class Tokenize1Test extends RouteBuilder {
                        @Override
                        public void configure()  {
                             from("direct:in").choice().when().xquery("text", Object.class, "namespace", "header")
                                    .to("mock:premium");
                        }
                    }
                """,
                """
                        import org.apache.camel.builder.RouteBuilder;

                        public class Jsonpath2Test extends RouteBuilder {
                            @Override
                            public void configure()  {
                                /*Some of the Java DSL for tokenize, xmlTokenize, xpath, xquery and jsonpath has been removed as part of making the DSL model consistent.
                        See https://camel.apache.org/manual/camel-4x-upgrade-guide-4_4.html#_camel_core for more details.*/from("direct:in").choice().when().jsonpathWriteAsString("something", true, "header")
                                        .to("mock:premium");
                            }
                        }
                        """));
    }

}
