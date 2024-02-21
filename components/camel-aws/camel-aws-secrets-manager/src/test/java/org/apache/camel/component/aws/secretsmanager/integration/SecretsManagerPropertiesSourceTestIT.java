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
package org.apache.camel.component.aws.secretsmanager.integration;

import org.apache.camel.FailedToCreateRouteException;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertThrows;

public class SecretsManagerPropertiesSourceTestIT extends CamelTestSupport {

    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AWS_ACCESS_KEY", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AWS_SECRET_KEY", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AWS_REGION", matches = ".*")
    @Test
    public void testFunction() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").setBody(simple("{{aws:hello}}")).to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("hello");

        template.sendBody("direct:start", "Hello World");

        MockEndpoint.assertIsSatisfied(context);
    }

    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AWS_ACCESS_KEY", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AWS_SECRET_KEY", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AWS_REGION", matches = ".*")
    @Test
    public void testComplexPropertiesFunction() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:username").setBody(simple("{{aws:database_sample/username}}")).to("mock:bar");
                from("direct:password").setBody(simple("{{aws:database_sample/password}}")).to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("admin", "password123");

        template.sendBody("direct:username", "Hello World");
        template.sendBody("direct:password", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AWS_ACCESS_KEY", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AWS_SECRET_KEY", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AWS_REGION", matches = ".*")
    @Test
    public void testComplexCustomPropertiesFunction() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:username").setBody(simple("{{aws:normalkey/username}}")).to("mock:bar");
                from("direct:password").setBody(simple("{{aws:normalkey/password}}")).to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("pippo", "pippo");

        template.sendBody("direct:username", "Hello World");
        template.sendBody("direct:password", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AWS_ACCESS_KEY", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AWS_SECRET_KEY", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AWS_REGION", matches = ".*")
    @Test
    public void testSecretNotFoundFunction() {
        Exception exception = assertThrows(FailedToCreateRouteException.class, () -> {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:start").setBody(simple("{{aws:testExample}}")).to("mock:bar");
                }
            });
            context.start();

            getMockEndpoint("mock:bar").expectedBodiesReceived("hello");

            template.sendBody("direct:start", "Hello World");

            MockEndpoint.assertIsSatisfied(context);
        });
    }

    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AWS_ACCESS_KEY", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AWS_SECRET_KEY", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AWS_REGION", matches = ".*")
    @Test
    public void testComplexNoSubkeyPropertiesFunction() {
        Exception exception = assertThrows(FailedToCreateRouteException.class, () -> {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:username").setBody(simple("{{aws:database_sample/not_existent}}")).to("mock:bar");
                }
            });
            context.start();

            getMockEndpoint("mock:bar").expectedBodiesReceived("admin");

            template.sendBody("direct:username", "Hello World");
            MockEndpoint.assertIsSatisfied(context);
        });
    }

    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AWS_ACCESS_KEY", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AWS_SECRET_KEY", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AWS_REGION", matches = ".*")
    @Test
    public void testComplexCustomPropertiesDefaultValueFunction() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:username").setBody(simple("{{aws:postgresql/additional1:admin}}")).to("mock:bar");
                from("direct:password").setBody(simple("{{aws:postgresql/additional2:secret}}")).to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("admin", "secret");

        template.sendBody("direct:username", "Hello World");
        template.sendBody("direct:password", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AWS_ACCESS_KEY", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AWS_SECRET_KEY", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AWS_REGION", matches = ".*")
    @Test
    public void testComplexCustomPropertiesDefaultValueExceptionFunction() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:username").setBody(simple("{{aws:test-3/additional1:admin}}")).to("mock:bar");
                from("direct:password").setBody(simple("{{aws:test-3/additional2:secret}}")).to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("admin", "secret");

        template.sendBody("direct:username", "Hello World");
        template.sendBody("direct:password", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AWS_ACCESS_KEY", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AWS_SECRET_KEY", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AWS_REGION", matches = ".*")
    @Test
    public void testComplexCustomPropertiesExceptionFunction() {
        Exception exception = assertThrows(FailedToCreateRouteException.class, () -> {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:username").setBody(simple("{{aws:test-3/additional1}}")).to("mock:bar");
                    from("direct:password").setBody(simple("{{aws:test-3/additional2}}")).to("mock:bar");
                }
            });
            context.start();

            getMockEndpoint("mock:bar").expectedBodiesReceived("admin", "secret");

            template.sendBody("direct:username", "Hello World");
            template.sendBody("direct:password", "Hello World");
            MockEndpoint.assertIsSatisfied(context);
        });
    }

    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AWS_ACCESS_KEY", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AWS_SECRET_KEY", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AWS_REGION", matches = ".*")
    @Test
    public void testComplexSimpleDefaultValueExceptionFunction() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:username").setBody(simple("{{aws:test-3:admin}}")).to("mock:bar");
                from("direct:password").setBody(simple("{{aws:test-1:secret}}")).to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("admin", "secret");

        template.sendBody("direct:username", "Hello World");
        template.sendBody("direct:password", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AWS_ACCESS_KEY", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AWS_SECRET_KEY", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AWS_REGION", matches = ".*")
    @Test
    public void testComplexSimpleNoDefaultValueExceptionFunction() {
        Exception exception = assertThrows(FailedToCreateRouteException.class, () -> {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:username").setBody(simple("{{aws:secretsuper}}")).to("mock:bar");
                }
            });
            context.start();

            getMockEndpoint("mock:bar").expectedBodiesReceived("admin");

            template.sendBody("direct:username", "Hello World");
            MockEndpoint.assertIsSatisfied(context);
        });
    }

    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AWS_ACCESS_KEY", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AWS_SECRET_KEY", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AWS_REGION", matches = ".*")
    @Test
    public void testComplexCustomPropertiesNoDefaultValueFunction() {
        Exception exception = assertThrows(FailedToCreateRouteException.class, () -> {
            context.addRoutes(new RouteBuilder() {
                @Override
                public void configure() {
                    from("direct:username").setBody(simple("{{aws:postgresql/additional1}}")).to("mock:bar");
                    from("direct:password").setBody(simple("{{aws:postgresql/additional2}}")).to("mock:bar");
                }
            });
            context.start();

            getMockEndpoint("mock:bar").expectedBodiesReceived("admin", "secret");

            template.sendBody("direct:username", "Hello World");
            template.sendBody("direct:password", "Hello World");
            MockEndpoint.assertIsSatisfied(context);
        });
    }

    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AWS_ACCESS_KEY", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AWS_SECRET_KEY", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AWS_REGION", matches = ".*")
    @Test
    public void testComplexCustomPropertiesNotExistentDefaultValueFunction() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:username").setBody(simple("{{aws:newsecret/additional1:admin}}")).to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("admin");

        template.sendBody("direct:username", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AWS_USE_DEFAULT_CREDENTIALS_PROVIDER", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AWS_REGION", matches = ".*")
    @Test
    public void testComplexCustomPropertiesDefaultCredsDefaultValueFunction() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:username").setBody(simple("{{aws:newsecret/additional1:admin}}")).to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("admin");

        template.sendBody("direct:username", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AWS_ACCESS_KEY", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AWS_SECRET_KEY", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AWS_REGION", matches = ".*")
    @Test
    public void testPropertiesWithDefaultFunction() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:username").setBody(simple("{{aws:postgresql/username:oscerd}}")).to("mock:bar");
                from("direct:password").setBody(simple("{{aws:postgresql/password:password}}")).to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("postgres", "secret");

        template.sendBody("direct:username", "Hello World");
        template.sendBody("direct:password", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AWS_ACCESS_KEY", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AWS_SECRET_KEY", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AWS_REGION", matches = ".*")
    @Test
    public void testPropertiesWithDefaultNotExistentFunction() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:username").setBody(simple("{{aws:db_sample/username:oscerd}}")).to("mock:bar");
                from("direct:password").setBody(simple("{{aws:db_sample/password:password}}")).to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("oscerd", "password");

        template.sendBody("direct:username", "Hello World");
        template.sendBody("direct:password", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AWS_ACCESS_KEY", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AWS_SECRET_KEY", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AWS_REGION", matches = ".*")
    @Test
    public void testPropertiesWithVersionFunction() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:version").setBody(simple("{{aws:test/id@e8d0e680-a504-4b70-a9b2-acf5efe0ba23}}")).to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("27");

        template.sendBody("direct:version", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AWS_ACCESS_KEY", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AWS_SECRET_KEY", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AWS_REGION", matches = ".*")
    @Test
    public void testPropertiesWithVersionAndNoFieldFunction() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:version").setBody(simple("{{aws:test@e8d0e680-a504-4b70-a9b2-acf5efe0ba23}}")).to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("{\"id\":\"27\"}");

        template.sendBody("direct:version", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AWS_ACCESS_KEY", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AWS_SECRET_KEY", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AWS_REGION", matches = ".*")
    @Test
    public void testPropertiesWithVersionNoFieldAndDefaultValueFunction() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:version").setBody(simple("{{aws:test:pippo@e8d0e680-a504-4b70-a9b2-acf5efe0ba23}}"))
                        .to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("{\"id\":\"27\"}");

        template.sendBody("direct:version", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AWS_ACCESS_KEY", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AWS_SECRET_KEY", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AWS_REGION", matches = ".*")
    @Test
    public void testPropertiesWithVersionNoFieldDefaultValueNotExistentSecretFunction() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:version").setBody(simple("{{aws:test1:pippo@e8d0e680-a504-4b70-a9b2-acf5efe0ba23}}"))
                        .to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("pippo");

        template.sendBody("direct:version", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AWS_ACCESS_KEY", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AWS_SECRET_KEY", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AWS_REGION", matches = ".*")
    @Test
    public void testPropertiesWithVersionNoFieldDefaultValueNotExistentVersionFunction() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:version").setBody(simple("{{aws:test1:pippo@e8d0e680-a504-4b70-a9b2-acf5efe0ba29}}"))
                        .to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("pippo");

        template.sendBody("direct:version", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }

    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AWS_ACCESS_KEY", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AWS_SECRET_KEY", matches = ".*")
    @EnabledIfEnvironmentVariable(named = "CAMEL_VAULT_AWS_REGION", matches = ".*")
    @Test
    public void testPropertiesWithVersionFieldAndDefaultValueFunction() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:version").setBody(simple("{{aws:test/id:pippo@e8d0e680-a504-4b70-a9b2-acf5efe0ba23}}"))
                        .to("mock:bar");
            }
        });
        context.start();

        getMockEndpoint("mock:bar").expectedBodiesReceived("27");

        template.sendBody("direct:version", "Hello World");
        MockEndpoint.assertIsSatisfied(context);
    }
}
