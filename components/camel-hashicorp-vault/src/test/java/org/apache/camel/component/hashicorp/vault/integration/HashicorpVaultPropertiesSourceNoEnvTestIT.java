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
package org.apache.camel.component.hashicorp.vault.integration;

import org.apache.camel.FailedToCreateRouteException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperties;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import static org.junit.jupiter.api.Assertions.assertThrows;

// Must be manually tested. Provide your own accessKey and secretKey using -Dcamel.vault.hashicorp.token, -Dcamel.vault.hashicorp.host, -Dcamel.vault.hashicorp.port and -Dcamel.vault.hashicorp.scheme
@EnabledIfSystemProperties({
        @EnabledIfSystemProperty(named = "camel.vault.hashicorp.token", matches = ".*",
                                 disabledReason = "Token not provided"),
        @EnabledIfSystemProperty(named = "camel.vault.hashicorp.host", matches = ".*", disabledReason = "Host not provided"),
        @EnabledIfSystemProperty(named = "camel.vault.hashicorp.port", matches = ".*", disabledReason = "Port not provided"),
        @EnabledIfSystemProperty(named = "camel.vault.hashicorp.scheme", matches = ".*", disabledReason = "Scheme not provided")
})
public class HashicorpVaultPropertiesSourceNoEnvTestIT extends CamelTestSupport {

    @Test
    public void testFunctio() throws Exception {
        context.getVaultConfiguration().hashicorp().setToken(System.getProperty("camel.vault.hashicorp.token"));
        context.getVaultConfiguration().hashicorp().setHost(System.getProperty("camel.vault.hashicorp.host"));
        context.getVaultConfiguration().hashicorp().setPort(System.getProperty("camel.vault.hashicorp.port"));
        context.getVaultConfiguration().hashicorp().setScheme(System.getProperty("camel.vault.hashicorp.scheme"));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").setBody(simple("{{hashicorp:secret:hello}}")).to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("{id=21, password=password, username=admin}");

        template.sendBody("direct:start", "Hello World");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testFunctionWithField() throws Exception {
        context.getVaultConfiguration().hashicorp().setToken(System.getProperty("camel.vault.hashicorp.token"));
        context.getVaultConfiguration().hashicorp().setHost(System.getProperty("camel.vault.hashicorp.host"));
        context.getVaultConfiguration().hashicorp().setPort(System.getProperty("camel.vault.hashicorp.port"));
        context.getVaultConfiguration().hashicorp().setScheme(System.getProperty("camel.vault.hashicorp.scheme"));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").setBody(simple("{{hashicorp:secret:hello#id}}")).to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("21");

        template.sendBody("direct:start", "Hello World");

        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testComplexCustomPropertiesFunction() throws Exception {
        context.getVaultConfiguration().hashicorp().setToken(System.getProperty("camel.vault.hashicorp.token"));
        context.getVaultConfiguration().hashicorp().setHost(System.getProperty("camel.vault.hashicorp.host"));
        context.getVaultConfiguration().hashicorp().setPort(System.getProperty("camel.vault.hashicorp.port"));
        context.getVaultConfiguration().hashicorp().setScheme(System.getProperty("camel.vault.hashicorp.scheme"));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:username").setBody(simple("{{hashicorp:secret:hello#username}}")).to("mock:bar");
                from("direct:password").setBody(simple("{{hashicorp:secret:hello#password}}")).to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("admin", "password");

        template.sendBody("direct:username", "Hello World");
        template.sendBody("direct:password", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testSecretNotFoundFunction() {
        Exception exception = assertThrows(FailedToCreateRouteException.class, () -> {
            context.getVaultConfiguration().hashicorp().setToken(System.getProperty("camel.vault.hashicorp.token"));
            context.getVaultConfiguration().hashicorp().setHost(System.getProperty("camel.vault.hashicorp.host"));
            context.getVaultConfiguration().hashicorp().setPort(System.getProperty("camel.vault.hashicorp.port"));
            context.getVaultConfiguration().hashicorp().setScheme(System.getProperty("camel.vault.hashicorp.scheme"));
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:start").setBody(simple("{{hashicorp:secret:testExample}}")).to("mock:bar");
                }
            });
            context.start();

            getMockEndpoint("mock:bar").expectedBodiesReceived("hello");

            template.sendBody("direct:start", "Hello World");

            MockEndpoint.assertIsSatisfied(context);
        });
    }

    @Test
    public void testComplexNoSubkeyPropertiesFunction() {
        Exception exception = assertThrows(FailedToCreateRouteException.class, () -> {
            context.getVaultConfiguration().hashicorp().setToken(System.getProperty("camel.vault.hashicorp.token"));
            context.getVaultConfiguration().hashicorp().setHost(System.getProperty("camel.vault.hashicorp.host"));
            context.getVaultConfiguration().hashicorp().setPort(System.getProperty("camel.vault.hashicorp.port"));
            context.getVaultConfiguration().hashicorp().setScheme(System.getProperty("camel.vault.hashicorp.scheme"));
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:username").setBody(simple("{{hashicorp:secret:database_sample/not_existent}}")).to("mock:bar");
                }
            });
            context.start();

            getMockEndpoint("mock:bar").expectedBodiesReceived("admin");

            template.sendBody("direct:username", "Hello World");
            MockEndpoint.assertIsSatisfied(context);
        });
    }

    @Test
    public void testComplexCustomPropertiesDefaultValueFunction() throws Exception {
        context.getVaultConfiguration().hashicorp().setToken(System.getProperty("camel.vault.hashicorp.token"));
        context.getVaultConfiguration().hashicorp().setHost(System.getProperty("camel.vault.hashicorp.host"));
        context.getVaultConfiguration().hashicorp().setPort(System.getProperty("camel.vault.hashicorp.port"));
        context.getVaultConfiguration().hashicorp().setScheme(System.getProperty("camel.vault.hashicorp.scheme"));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:username").setBody(simple("{{hashicorp:secret:hello/additional1:admin}}")).to("mock:bar");
                from("direct:password").setBody(simple("{{hashicorp:secret:hello/additional2:secret}}")).to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("admin", "secret");

        template.sendBody("direct:username", "Hello World");
        template.sendBody("direct:password", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testComplexCustomPropertiesDefaultValueExceptionFunction() throws Exception {
        context.getVaultConfiguration().hashicorp().setToken(System.getProperty("camel.vault.hashicorp.token"));
        context.getVaultConfiguration().hashicorp().setHost(System.getProperty("camel.vault.hashicorp.host"));
        context.getVaultConfiguration().hashicorp().setPort(System.getProperty("camel.vault.hashicorp.port"));
        context.getVaultConfiguration().hashicorp().setScheme(System.getProperty("camel.vault.hashicorp.scheme"));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:username").setBody(simple("{{hashicorp:secret:test-3/additional1:admin}}")).to("mock:bar");
                from("direct:password").setBody(simple("{{hashicorp:secret:test-3/additional2:secret}}")).to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("admin", "secret");

        template.sendBody("direct:username", "Hello World");
        template.sendBody("direct:password", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testComplexCustomPropertiesExceptionFunction() {
        Exception exception = assertThrows(FailedToCreateRouteException.class, () -> {
            context.getVaultConfiguration().hashicorp().setToken(System.getProperty("camel.vault.hashicorp.token"));
            context.getVaultConfiguration().hashicorp().setHost(System.getProperty("camel.vault.hashicorp.host"));
            context.getVaultConfiguration().hashicorp().setPort(System.getProperty("camel.vault.hashicorp.port"));
            context.getVaultConfiguration().hashicorp().setScheme(System.getProperty("camel.vault.hashicorp.scheme"));
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:username").setBody(simple("{{hashicorp:secret:test-3/additional1}}")).to("mock:bar");
                    from("direct:password").setBody(simple("{{hashicorp:secret:test-3/additional2}}")).to("mock:bar");
                }
            });
            context.start();

            getMockEndpoint("mock:bar").expectedBodiesReceived("admin", "secret");

            template.sendBody("direct:username", "Hello World");
            template.sendBody("direct:password", "Hello World");
            MockEndpoint.assertIsSatisfied(context);
        });
    }

    @Test
    public void testComplexSimpleDefaultValueExceptionFunction() throws Exception {
        context.getVaultConfiguration().hashicorp().setToken(System.getProperty("camel.vault.hashicorp.token"));
        context.getVaultConfiguration().hashicorp().setHost(System.getProperty("camel.vault.hashicorp.host"));
        context.getVaultConfiguration().hashicorp().setPort(System.getProperty("camel.vault.hashicorp.port"));
        context.getVaultConfiguration().hashicorp().setScheme(System.getProperty("camel.vault.hashicorp.scheme"));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:username").setBody(simple("{{hashicorp:secret:hello-2:admin}}")).to("mock:bar");
                from("direct:password").setBody(simple("{{hashicorp:secret:hello-1:secret}}")).to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("admin", "secret");

        template.sendBody("direct:username", "Hello World");
        template.sendBody("direct:password", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testComplexSimpleNoDefaultValueExceptionFunction() {
        Exception exception = assertThrows(FailedToCreateRouteException.class, () -> {
            context.getVaultConfiguration().hashicorp().setToken(System.getProperty("camel.vault.hashicorp.token"));
            context.getVaultConfiguration().hashicorp().setHost(System.getProperty("camel.vault.hashicorp.host"));
            context.getVaultConfiguration().hashicorp().setPort(System.getProperty("camel.vault.hashicorp.port"));
            context.getVaultConfiguration().hashicorp().setScheme(System.getProperty("camel.vault.hashicorp.scheme"));
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:username").setBody(simple("{{hashicorp:secret:secretsuper}}")).to("mock:bar");
                }
            });
            context.start();

            getMockEndpoint("mock:bar").expectedBodiesReceived("admin");

            template.sendBody("direct:username", "Hello World");
            MockEndpoint.assertIsSatisfied(context);
        });
    }

    @Test
    public void testComplexCustomPropertiesNoDefaultValueFunction() {
        Exception exception = assertThrows(FailedToCreateRouteException.class, () -> {
            context.getVaultConfiguration().hashicorp().setToken(System.getProperty("camel.vault.hashicorp.token"));
            context.getVaultConfiguration().hashicorp().setHost(System.getProperty("camel.vault.hashicorp.host"));
            context.getVaultConfiguration().hashicorp().setPort(System.getProperty("camel.vault.hashicorp.port"));
            context.getVaultConfiguration().hashicorp().setScheme(System.getProperty("camel.vault.hashicorp.scheme"));
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:username").setBody(simple("{{hashicorp:secret:postgresql/additional1}}")).to("mock:bar");
                    from("direct:password").setBody(simple("{{hashicorp:secret:postgresql/additional2}}")).to("mock:bar");
                }
            });
            context.start();

            getMockEndpoint("mock:bar").expectedBodiesReceived("admin", "secret");

            template.sendBody("direct:username", "Hello World");
            template.sendBody("direct:password", "Hello World");
            MockEndpoint.assertIsSatisfied(context);
        });
    }

    @Test
    public void testComplexCustomPropertiesNotExistentDefaultValueFunction() throws Exception {
        context.getVaultConfiguration().hashicorp().setToken(System.getProperty("camel.vault.hashicorp.token"));
        context.getVaultConfiguration().hashicorp().setHost(System.getProperty("camel.vault.hashicorp.host"));
        context.getVaultConfiguration().hashicorp().setPort(System.getProperty("camel.vault.hashicorp.port"));
        context.getVaultConfiguration().hashicorp().setScheme(System.getProperty("camel.vault.hashicorp.scheme"));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:username").setBody(simple("{{hashicorp:secret:newsecret/additional1:admin}}")).to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("admin");

        template.sendBody("direct:username", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testComplexCustomPropertiesDefaultCredsDefaultValueFunction() throws Exception {
        context.getVaultConfiguration().hashicorp().setToken(System.getProperty("camel.vault.hashicorp.token"));
        context.getVaultConfiguration().hashicorp().setHost(System.getProperty("camel.vault.hashicorp.host"));
        context.getVaultConfiguration().hashicorp().setPort(System.getProperty("camel.vault.hashicorp.port"));
        context.getVaultConfiguration().hashicorp().setScheme(System.getProperty("camel.vault.hashicorp.scheme"));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:username").setBody(simple("{{hashicorp:secret:newsecret/additional1:admin}}")).to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("admin");

        template.sendBody("direct:username", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testPropertiesWithDefaultFunction() throws Exception {
        context.getVaultConfiguration().hashicorp().setToken(System.getProperty("camel.vault.hashicorp.token"));
        context.getVaultConfiguration().hashicorp().setHost(System.getProperty("camel.vault.hashicorp.host"));
        context.getVaultConfiguration().hashicorp().setPort(System.getProperty("camel.vault.hashicorp.port"));
        context.getVaultConfiguration().hashicorp().setScheme(System.getProperty("camel.vault.hashicorp.scheme"));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:username").setBody(simple("{{hashicorp:secret:postgresql/username:oscerd}}")).to("mock:bar");
                from("direct:password").setBody(simple("{{hashicorp:secret:postgresql/password:password}}")).to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("oscerd", "password");

        template.sendBody("direct:username", "Hello World");
        template.sendBody("direct:password", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testPropertiesWithDefaultNotExistentFunction() throws Exception {
        context.getVaultConfiguration().hashicorp().setToken(System.getProperty("camel.vault.hashicorp.token"));
        context.getVaultConfiguration().hashicorp().setHost(System.getProperty("camel.vault.hashicorp.host"));
        context.getVaultConfiguration().hashicorp().setPort(System.getProperty("camel.vault.hashicorp.port"));
        context.getVaultConfiguration().hashicorp().setScheme(System.getProperty("camel.vault.hashicorp.scheme"));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:username").setBody(simple("{{hashicorp:secret:db_sample/username:oscerd}}")).to("mock:bar");
                from("direct:password").setBody(simple("{{hashicorp:secret:db_sample/password:password}}")).to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("oscerd", "password");

        template.sendBody("direct:username", "Hello World");
        template.sendBody("direct:password", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testPropertiesWithVersionFunction() throws Exception {
        context.getVaultConfiguration().hashicorp().setToken(System.getProperty("camel.vault.hashicorp.token"));
        context.getVaultConfiguration().hashicorp().setHost(System.getProperty("camel.vault.hashicorp.host"));
        context.getVaultConfiguration().hashicorp().setPort(System.getProperty("camel.vault.hashicorp.port"));
        context.getVaultConfiguration().hashicorp().setScheme(System.getProperty("camel.vault.hashicorp.scheme"));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:version").setBody(simple("{{hashicorp:secret:hello#id@1}}")).to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("21");

        template.sendBody("direct:version", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testPropertiesWithVersionAndNoFieldFunction() throws Exception {
        context.getVaultConfiguration().hashicorp().setToken(System.getProperty("camel.vault.hashicorp.token"));
        context.getVaultConfiguration().hashicorp().setHost(System.getProperty("camel.vault.hashicorp.host"));
        context.getVaultConfiguration().hashicorp().setPort(System.getProperty("camel.vault.hashicorp.port"));
        context.getVaultConfiguration().hashicorp().setScheme(System.getProperty("camel.vault.hashicorp.scheme"));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:version").setBody(simple("{{hashicorp:secret:hello@1}}")).to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("{id=21, password=password, username=admin}");

        template.sendBody("direct:version", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testPropertiesWithVersionNoFieldAndDefaultValueFunction() throws Exception {
        context.getVaultConfiguration().hashicorp().setToken(System.getProperty("camel.vault.hashicorp.token"));
        context.getVaultConfiguration().hashicorp().setHost(System.getProperty("camel.vault.hashicorp.host"));
        context.getVaultConfiguration().hashicorp().setPort(System.getProperty("camel.vault.hashicorp.port"));
        context.getVaultConfiguration().hashicorp().setScheme(System.getProperty("camel.vault.hashicorp.scheme"));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:version").setBody(simple("{{hashicorp:secret:hello:pippo@1}}"))
                        .to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("{id=21, password=password, username=admin}");

        template.sendBody("direct:version", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testPropertiesWithVersionNoFieldDefaultValueNotExistentSecretFunction() throws Exception {
        context.getVaultConfiguration().hashicorp().setToken(System.getProperty("camel.vault.hashicorp.token"));
        context.getVaultConfiguration().hashicorp().setHost(System.getProperty("camel.vault.hashicorp.host"));
        context.getVaultConfiguration().hashicorp().setPort(System.getProperty("camel.vault.hashicorp.port"));
        context.getVaultConfiguration().hashicorp().setScheme(System.getProperty("camel.vault.hashicorp.scheme"));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:version").setBody(simple("{{hashicorp:secret:hello-3:pippo@4}}"))
                        .to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("pippo");

        template.sendBody("direct:version", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testPropertiesWithVersionNoFieldDefaultValueNotExistentVersionFunction() throws Exception {
        context.getVaultConfiguration().hashicorp().setToken(System.getProperty("camel.vault.hashicorp.token"));
        context.getVaultConfiguration().hashicorp().setHost(System.getProperty("camel.vault.hashicorp.host"));
        context.getVaultConfiguration().hashicorp().setPort(System.getProperty("camel.vault.hashicorp.port"));
        context.getVaultConfiguration().hashicorp().setScheme(System.getProperty("camel.vault.hashicorp.scheme"));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:version").setBody(simple("{{hashicorp:secret:hello:pippo@4}}"))
                        .to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("pippo");

        template.sendBody("direct:version", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testPropertiesWithVersionFieldAndDefaultValueFunction() throws Exception {
        context.getVaultConfiguration().hashicorp().setToken(System.getProperty("camel.vault.hashicorp.token"));
        context.getVaultConfiguration().hashicorp().setHost(System.getProperty("camel.vault.hashicorp.host"));
        context.getVaultConfiguration().hashicorp().setPort(System.getProperty("camel.vault.hashicorp.port"));
        context.getVaultConfiguration().hashicorp().setScheme(System.getProperty("camel.vault.hashicorp.scheme"));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:version").setBody(simple("{{hashicorp:secret:hello#id:pippo@1}}"))
                        .to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("21");

        template.sendBody("direct:version", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testPropertiesWithVersionFieldAndDefaultValueFunctionWithNotExistentVersion() throws Exception {
        context.getVaultConfiguration().hashicorp().setToken(System.getProperty("camel.vault.hashicorp.token"));
        context.getVaultConfiguration().hashicorp().setHost(System.getProperty("camel.vault.hashicorp.host"));
        context.getVaultConfiguration().hashicorp().setPort(System.getProperty("camel.vault.hashicorp.port"));
        context.getVaultConfiguration().hashicorp().setScheme(System.getProperty("camel.vault.hashicorp.scheme"));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:version").setBody(simple("{{hashicorp:secret:hello#id:pippo@2}}"))
                        .to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("pippo");

        template.sendBody("direct:version", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testPropertiesWithVersionFieldAndDefaultValueWithSlashInName() throws Exception {
        context.getVaultConfiguration().hashicorp().setToken(System.getProperty("camel.vault.hashicorp.token"));
        context.getVaultConfiguration().hashicorp().setHost(System.getProperty("camel.vault.hashicorp.host"));
        context.getVaultConfiguration().hashicorp().setPort(System.getProperty("camel.vault.hashicorp.port"));
        context.getVaultConfiguration().hashicorp().setScheme(System.getProperty("camel.vault.hashicorp.scheme"));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:version").setBody(simple("{{hashicorp:secret:production/secrets/hello#id:pippo@1}}"))
                        .to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("21");

        template.sendBody("direct:version", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testPropertiesWithDoubleEngines() throws Exception {
        context.getVaultConfiguration().hashicorp().setToken(System.getProperty("camel.vault.hashicorp.token"));
        context.getVaultConfiguration().hashicorp().setHost(System.getProperty("camel.vault.hashicorp.host"));
        context.getVaultConfiguration().hashicorp().setPort(System.getProperty("camel.vault.hashicorp.port"));
        context.getVaultConfiguration().hashicorp().setScheme(System.getProperty("camel.vault.hashicorp.scheme"));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:engine1").setBody(simple("{{hashicorp:secretengine1:hello}}"))
                        .to("mock:bar");

                from("direct:engine2").setBody(simple("{{hashicorp:secretengine2:hello}}"))
                        .to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("{id=21}", "{id=21}");

        template.sendBody("direct:engine1", "Hello World");
        template.sendBody("direct:engine2", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @Test
    public void testPropertiesWithVersionFieldAndDefaultValueWithVersion() throws Exception {
        context.getVaultConfiguration().hashicorp().setToken(System.getProperty("camel.vault.hashicorp.token"));
        context.getVaultConfiguration().hashicorp().setHost(System.getProperty("camel.vault.hashicorp.host"));
        context.getVaultConfiguration().hashicorp().setPort(System.getProperty("camel.vault.hashicorp.port"));
        context.getVaultConfiguration().hashicorp().setScheme(System.getProperty("camel.vault.hashicorp.scheme"));
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:version").setBody(simple("{{hashicorp:secret:production/secrets/hello@1}}"))
                        .to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("{id=21}");

        template.sendBody("direct:version", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }
}
