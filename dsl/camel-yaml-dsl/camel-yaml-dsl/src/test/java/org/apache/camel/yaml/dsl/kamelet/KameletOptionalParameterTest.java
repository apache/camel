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
package org.apache.camel.yaml.dsl.kamelet;

import org.apache.camel.Endpoint;
import org.apache.camel.RoutesBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.aws2.s3.AWS2S3Endpoint;
import org.apache.camel.component.log.LogEndpoint;
import org.apache.camel.impl.engine.DefaultSupervisingRouteController;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class KameletOptionalParameterTest extends CamelTestSupport {

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testOptionalParamsNotProvided() throws Exception {
        context.addRoutes(createRouteBuilder());
        context.start();

        Endpoint e = context.getEndpoints().stream()
                .filter(p -> p instanceof LogEndpoint)
                .findFirst().orElse(null);
        LogEndpoint log = Assertions.assertInstanceOf(LogEndpoint.class, e);
        Assertions.assertFalse(log.isShowHeaders(), "showHeaders should default to false when not provided");
        Assertions.assertFalse(log.isShowStreams(), "showStreams should default to false when not provided");
    }

    @Test
    public void testOptionalParamOneProvided() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("kamelet:log-sink?showHeaders=true")
                        .to("mock:end");
            }
        });
        context.start();

        Endpoint e = context.getEndpoints().stream()
                .filter(p -> p instanceof LogEndpoint)
                .findFirst().orElse(null);
        LogEndpoint log = Assertions.assertInstanceOf(LogEndpoint.class, e);
        Assertions.assertTrue(log.isShowHeaders(), "showHeaders should be true when provided");
        Assertions.assertFalse(log.isShowStreams(), "showStreams should default to false when not provided");
    }

    @Test
    public void testOptionalParamsBothProvided() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("kamelet:log-sink?showHeaders=true&showStreams=true")
                        .to("mock:end");
            }
        });
        context.start();

        Endpoint e = context.getEndpoints().stream()
                .filter(p -> p instanceof LogEndpoint)
                .findFirst().orElse(null);
        LogEndpoint log = Assertions.assertInstanceOf(LogEndpoint.class, e);
        Assertions.assertTrue(log.isShowHeaders(), "showHeaders should be true when provided");
        Assertions.assertTrue(log.isShowStreams(), "showStreams should be true when provided");
    }

    /**
     * Tests that optional secret parameters (format: password) in a kamelet are properly stripped when not provided.
     * The my-aws-s3-source kamelet has optional secret params (accessKey, cheeseKey, sessionToken) that use {{?xxx}}
     * syntax and get RAW()-wrapped by YamlSupport.createEndpointUri(). When not provided, they should be removed from
     * the endpoint URI.
     *
     * See: https://github.com/apache/camel-kamelets/issues/2869
     */
    @Test
    public void testAwsOptionalSecretParamsNotProvided() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("kamelet:my-aws-s3-source?bucketNameOrArn=mybucket&region=eu-south-2&autoCreateBucket=false"
                     + "&useDefaultCredentialsProvider=true")
                        .to("mock:result");
            }
        });
        context.setAutoStartup(false);
        context.setRouteController(new DefaultSupervisingRouteController());
        context.start();

        Endpoint e = context.getEndpoints().stream()
                .filter(p -> p instanceof AWS2S3Endpoint)
                .findFirst().orElse(null);
        AWS2S3Endpoint s3 = Assertions.assertInstanceOf(AWS2S3Endpoint.class, e);
        // Verify the optional secret params are NOT in the endpoint URI
        String uri = s3.getEndpointUri();
        Assertions.assertFalse(uri.contains("accessKey"), "accessKey should not be in URI when not provided: " + uri);
        Assertions.assertFalse(uri.contains("secretKey"), "secretKey should not be in URI when not provided: " + uri);
        Assertions.assertFalse(uri.contains("sessionToken"), "sessionToken should not be in URI when not provided: " + uri);
        Assertions.assertNull(s3.getConfiguration().getAccessKey(),
                "accessKey should be null when optional param not provided");
        Assertions.assertNull(s3.getConfiguration().getSecretKey(),
                "secretKey should be null when optional param not provided");
    }

    @Test
    public void testAwsOptionalSecretParamsProvided() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                context.getPropertiesComponent().addInitialProperty("aws.accessKeyId", "my@+id");
                context.getPropertiesComponent().addInitialProperty("aws.secretAccessKey", "my%^+|key");

                from("kamelet:my-aws-s3-source?bucketNameOrArn=mybucket&region=eu-south-2&autoCreateBucket=false"
                     + "&accessKey={{aws.accessKeyId}}&cheeseKey={{aws.secretAccessKey}}")
                        .to("mock:result");
            }
        });
        context.setAutoStartup(false);
        context.setRouteController(new DefaultSupervisingRouteController());
        context.start();

        Endpoint e = context.getEndpoints().stream()
                .filter(p -> p instanceof AWS2S3Endpoint)
                .findFirst().orElse(null);
        AWS2S3Endpoint s3 = Assertions.assertInstanceOf(AWS2S3Endpoint.class, e);
        Assertions.assertEquals("my@+id", s3.getConfiguration().getAccessKey());
        Assertions.assertEquals("my%^+|key", s3.getConfiguration().getSecretKey());
    }

    @Override
    protected RoutesBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .to("kamelet:log-sink")
                        .to("mock:end");
            }
        };
    }
}
