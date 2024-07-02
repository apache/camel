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
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;
import org.openrewrite.test.TypeValidation;

import static org.openrewrite.java.Assertions.java;

public class CamelHttpTest implements RewriteTest {

    @Override
    public void defaults(RecipeSpec spec) {
        CamelTestUtil.recipe(spec, CamelTestUtil.CamelVersion.v4_0)
                .parser(CamelTestUtil.parserFromClasspath(CamelTestUtil.CamelVersion.v3_18,
                        "camel-api", "camel-support", "camel-core-model", "camel-util", "camel-catalog", "camel-main",
                        "httpclient-4.5.14", "httpcore-4.4.16"))
                .typeValidationOptions(TypeValidation.none());
    }

    @Test
    void testHttp() {
        //language=java
        rewriteRun(java(
                """
                            import jakarta.inject.Named;

                            import org.apache.http.HttpHost;
                            import org.apache.http.auth.AuthScope;
                            import org.apache.http.auth.UsernamePasswordCredentials;
                            import org.apache.http.client.protocol.HttpClientContext;
                            import org.apache.http.impl.auth.BasicScheme;
                            import org.apache.http.impl.client.BasicAuthCache;
                            import org.apache.http.impl.client.BasicCredentialsProvider;
                            import org.apache.http.protocol.HttpContext;
                            import org.eclipse.microprofile.config.ConfigProvider;

                            public class HttpProducers {

                                @Named
                                HttpContext basicAuthContext() {
                                    Integer port = ConfigProvider.getConfig().getValue("quarkus.http.test-port", Integer.class);

                                    UsernamePasswordCredentials credentials = new UsernamePasswordCredentials("USER_ADMIN", "USER_ADMIN_PASSWORD");
                                    BasicCredentialsProvider provider = new BasicCredentialsProvider();
                                    provider.setCredentials(AuthScope.ANY, credentials);

                                    BasicAuthCache authCache = new BasicAuthCache();
                                    BasicScheme basicAuth = new BasicScheme();
                                    authCache.put(new HttpHost("localhost", port), basicAuth);

                                    HttpClientContext context = HttpClientContext.create();
                                    context.setAuthCache(authCache);
                                    context.setCredentialsProvider(provider);

                                    return context;
                                }
                            }
                        """,
                """
                            import jakarta.inject.Named;
                            import org.apache.hc.client5.http.auth.AuthScope;
                            import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
                            import org.apache.hc.client5.http.impl.auth.BasicAuthCache;
                            import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
                            import org.apache.hc.client5.http.impl.auth.BasicScheme;
                            import org.apache.hc.client5.http.protocol.HttpClientContext;
                            import org.apache.hc.core5.http.HttpHost;
                            import org.apache.hc.core5.http.protocol.HttpContext;
                            import org.eclipse.microprofile.config.ConfigProvider;

                            public class HttpProducers {

                                @Named
                                HttpContext basicAuthContext() {
                                    Integer port = ConfigProvider.getConfig().getValue("quarkus.http.test-port", Integer.class);

                                    UsernamePasswordCredentials credentials = new UsernamePasswordCredentials("USER_ADMIN", "USER_ADMIN_PASSWORD");
                                    BasicCredentialsProvider provider = new BasicCredentialsProvider();
                                    provider.setCredentials(new AuthScope(null, -1), credentials);

                                    BasicAuthCache authCache = new BasicAuthCache();
                                    BasicScheme basicAuth = new BasicScheme();
                                    authCache.put(new HttpHost("localhost", port), basicAuth);

                                    HttpClientContext context = HttpClientContext.create();
                                    context.setAuthCache(authCache);
                                    context.setCredentialsProvider(provider);

                                    return context;
                                }
                            }
                        """));
    }

    @Test
    void testNoopHostnameVerifier() {
        //language=java
        rewriteRun(java(
                """
                            import jakarta.inject.Named;
                            import org.apache.camel.CamelContext;
                            import org.apache.http.conn.ssl.NoopHostnameVerifier;
                            import org.eclipse.microprofile.config.ConfigProvider;

                            public class HttpProducers {

                                CamelContext context;

                                @Named
                                public NoopHostnameVerifier x509HostnameVerifier() {
                                    return NoopHostnameVerifier.INSTANCE;
                                }
                            }
                        """,
                """
                            import jakarta.inject.Named;
                            import org.apache.camel.CamelContext;
                            import org.apache.hc.client5.http.conn.ssl.NoopHostnameVerifier;
                            import org.eclipse.microprofile.config.ConfigProvider;

                            public class HttpProducers {

                                CamelContext context;

                                @Named
                                public NoopHostnameVerifier x509HostnameVerifier() {
                                    return NoopHostnameVerifier.INSTANCE;
                                }
                            }
                        """));
    }
}
