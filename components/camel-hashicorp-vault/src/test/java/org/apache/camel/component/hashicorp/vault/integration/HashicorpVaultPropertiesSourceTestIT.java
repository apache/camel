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
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class HashicorpVaultPropertiesSourceTestIT extends CamelTestSupport {

    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_TOKEN", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_ENGINE", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_HOST", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_PORT", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_SCHEME", matches = ".*")
    @Test
    public void testFunctio() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").setBody(simple("{{hashicorp:hello}}")).to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("{id=21, password=password, username=admin}");

        template.sendBody("direct:start", "Hello World");

        MockEndpoint.assertIsSatisfied(context);
    }

    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_TOKEN", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_ENGINE", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_HOST", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_PORT", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_SCHEME", matches = ".*")
    @Test
    public void testFunctionWithField() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").setBody(simple("{{hashicorp:hello/id}}")).to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("21");

        template.sendBody("direct:start", "Hello World");

        MockEndpoint.assertIsSatisfied(context);
    }

    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_TOKEN", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_ENGINE", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_HOST", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_PORT", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_SCHEME", matches = ".*")
    @Test
    public void testComplexCustomPropertiesFunction() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:username").setBody(simple("{{hashicorp:hello/username}}")).to("mock:bar");
                from("direct:password").setBody(simple("{{hashicorp:hello/password}}")).to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("admin", "password");

        template.sendBody("direct:username", "Hello World");
        template.sendBody("direct:password", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_TOKEN", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_ENGINE", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_HOST", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_PORT", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_SCHEME", matches = ".*")
    @Test
    public void testSecretNotFoundFunction() {
        Exception exception = assertThrows(FailedToCreateRouteException.class, () -> {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:start").setBody(simple("{{hashicorp:testExample}}")).to("mock:bar");
                }
            });
            context.start();

            getMockEndpoint("mock:bar").expectedBodiesReceived("hello");

            template.sendBody("direct:start", "Hello World");

            MockEndpoint.assertIsSatisfied(context);
        });
    }

    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_TOKEN", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_ENGINE", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_HOST", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_PORT", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_SCHEME", matches = ".*")
    @Test
    public void testComplexNoSubkeyPropertiesFunction() {
        Exception exception = assertThrows(FailedToCreateRouteException.class, () -> {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:username").setBody(simple("{{hashicorp:database_sample/not_existent}}")).to("mock:bar");
                }
            });
            context.start();

            getMockEndpoint("mock:bar").expectedBodiesReceived("admin");

            template.sendBody("direct:username", "Hello World");
            MockEndpoint.assertIsSatisfied(context);
        });
    }

    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_TOKEN", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_ENGINE", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_HOST", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_PORT", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_SCHEME", matches = ".*")
    @Test
    public void testComplexCustomPropertiesDefaultValueFunction() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:username").setBody(simple("{{hashicorp:hello/additional1:admin}}")).to("mock:bar");
                from("direct:password").setBody(simple("{{hashicorp:hello/additional2:secret}}")).to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("admin", "secret");

        template.sendBody("direct:username", "Hello World");
        template.sendBody("direct:password", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_TOKEN", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_ENGINE", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_HOST", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_PORT", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_SCHEME", matches = ".*")
    @Test
    public void testComplexCustomPropertiesDefaultValueExceptionFunction() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:username").setBody(simple("{{hashicorp:test-3/additional1:admin}}")).to("mock:bar");
                from("direct:password").setBody(simple("{{hashicorp:test-3/additional2:secret}}")).to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("admin", "secret");

        template.sendBody("direct:username", "Hello World");
        template.sendBody("direct:password", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_TOKEN", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_ENGINE", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_HOST", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_PORT", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_SCHEME", matches = ".*")
    @Test
    public void testComplexCustomPropertiesExceptionFunction() {
        Exception exception = assertThrows(FailedToCreateRouteException.class, () -> {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:username").setBody(simple("{{hashicorp:test-3/additional1}}")).to("mock:bar");
                    from("direct:password").setBody(simple("{{hashicorp:test-3/additional2}}")).to("mock:bar");
                }
            });
            context.start();

            getMockEndpoint("mock:bar").expectedBodiesReceived("admin", "secret");

            template.sendBody("direct:username", "Hello World");
            template.sendBody("direct:password", "Hello World");
            MockEndpoint.assertIsSatisfied(context);
        });
    }

    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_TOKEN", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_ENGINE", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_HOST", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_PORT", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_SCHEME", matches = ".*")
    @Test
    public void testComplexSimpleDefaultValueExceptionFunction() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:username").setBody(simple("{{hashicorp:hello-2:admin}}")).to("mock:bar");
                from("direct:password").setBody(simple("{{hashicorp:hello-1:secret}}")).to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("admin", "secret");

        template.sendBody("direct:username", "Hello World");
        template.sendBody("direct:password", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_TOKEN", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_ENGINE", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_HOST", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_PORT", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_SCHEME", matches = ".*")
    @Test
    public void testComplexSimpleNoDefaultValueExceptionFunction() {
        Exception exception = assertThrows(FailedToCreateRouteException.class, () -> {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:username").setBody(simple("{{hashicorp:secretsuper}}")).to("mock:bar");
                }
            });
            context.start();

            getMockEndpoint("mock:bar").expectedBodiesReceived("admin");

            template.sendBody("direct:username", "Hello World");
            MockEndpoint.assertIsSatisfied(context);
        });
    }

    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_TOKEN", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_ENGINE", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_HOST", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_PORT", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_SCHEME", matches = ".*")
    @Test
    public void testComplexCustomPropertiesNoDefaultValueFunction() {
        Exception exception = assertThrows(FailedToCreateRouteException.class, () -> {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:username").setBody(simple("{{hashicorp:postgresql/additional1}}")).to("mock:bar");
                    from("direct:password").setBody(simple("{{hashicorp:postgresql/additional2}}")).to("mock:bar");
                }
            });
            context.start();

            getMockEndpoint("mock:bar").expectedBodiesReceived("admin", "secret");

            template.sendBody("direct:username", "Hello World");
            template.sendBody("direct:password", "Hello World");
            MockEndpoint.assertIsSatisfied(context);
        });
    }

    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_TOKEN", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_ENGINE", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_HOST", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_PORT", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_SCHEME", matches = ".*")
    @Test
    public void testComplexCustomPropertiesNotExistentDefaultValueFunction() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:username").setBody(simple("{{hashicorp:newsecret/additional1:admin}}")).to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("admin");

        template.sendBody("direct:username", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_TOKEN", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_ENGINE", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_HOST", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_PORT", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_SCHEME", matches = ".*")
    @Test
    public void testComplexCustomPropertiesDefaultCredsDefaultValueFunction() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:username").setBody(simple("{{hashicorp:newsecret/additional1:admin}}")).to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("admin");

        template.sendBody("direct:username", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_TOKEN", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_ENGINE", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_HOST", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_PORT", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_SCHEME", matches = ".*")
    @Test
    public void testPropertiesWithDefaultFunction() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:username").setBody(simple("{{hashicorp:postgresql/username:oscerd}}")).to("mock:bar");
                from("direct:password").setBody(simple("{{hashicorp:postgresql/password:password}}")).to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("oscerd", "password");

        template.sendBody("direct:username", "Hello World");
        template.sendBody("direct:password", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_TOKEN", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_ENGINE", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_HOST", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_PORT", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_SCHEME", matches = ".*")
    @Test
    public void testPropertiesWithDefaultNotExistentFunction() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:username").setBody(simple("{{hashicorp:db_sample/username:oscerd}}")).to("mock:bar");
                from("direct:password").setBody(simple("{{hashicorp:db_sample/password:password}}")).to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("oscerd", "password");

        template.sendBody("direct:username", "Hello World");
        template.sendBody("direct:password", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_TOKEN", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_ENGINE", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_HOST", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_PORT", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_SCHEME", matches = ".*")
    @Test
    public void testPropertiesWithVersionFunction() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:version").setBody(simple("{{hashicorp:hello/id@1}}")).to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("21");

        template.sendBody("direct:version", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_TOKEN", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_ENGINE", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_HOST", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_PORT", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_SCHEME", matches = ".*")
    @Test
    public void testPropertiesWithVersionAndNoFieldFunction() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:version").setBody(simple("{{hashicorp:hello@1}}")).to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("{id=21}");

        template.sendBody("direct:version", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_TOKEN", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_ENGINE", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_HOST", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_PORT", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_SCHEME", matches = ".*")
    @Test
    public void testPropertiesWithVersionNoFieldAndDefaultValueFunction() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:version").setBody(simple("{{hashicorp:hello:pippo@1}}"))
                        .to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("{id=21}");

        template.sendBody("direct:version", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_TOKEN", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_ENGINE", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_HOST", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_PORT", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_SCHEME", matches = ".*")
    @Test
    public void testPropertiesWithVersionNoFieldDefaultValueNotExistentSecretFunction() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:version").setBody(simple("{{hashicorp:hello-3:pippo@4}}"))
                        .to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("pippo");

        template.sendBody("direct:version", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_TOKEN", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_ENGINE", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_HOST", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_PORT", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_SCHEME", matches = ".*")
    @Test
    public void testPropertiesWithVersionNoFieldDefaultValueNotExistentVersionFunction() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:version").setBody(simple("{{hashicorp:hello:pippo@4}}"))
                        .to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("pippo");

        template.sendBody("direct:version", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_TOKEN", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_ENGINE", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_HOST", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_PORT", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_HASHICORP_VAULT_SCHEME", matches = ".*")
    @Test
    public void testPropertiesWithVersionFieldAndDefaultValueFunction() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:version").setBody(simple("{{hashicorp:hello/id:pippo@1}}"))
                        .to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("21");

        template.sendBody("direct:version", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }
}
