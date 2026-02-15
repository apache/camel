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
import org.apache.camel.impl.engine.DefaultSupervisingRouteController;
import org.apache.camel.test.junit6.CamelTestSupport;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class AwsRawSecretTest extends CamelTestSupport {

    private static final String KEY_1 = "my@+id";
    private static final String KEY_2 = "my%^+|key";

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testAwsRawSecret() throws Exception {
        context.addRoutes(createRouteBuilder());
        context.setAutoStartup(false);
        // cannot startup as ca
        context.setRouteController(new DefaultSupervisingRouteController());
        context.start();

        Endpoint e = context.getEndpoints().stream().filter(p -> p instanceof AWS2S3Endpoint).findFirst().orElse(null);
        AWS2S3Endpoint a = Assertions.assertInstanceOf(AWS2S3Endpoint.class, e);
        Assertions.assertEquals(KEY_1, a.getConfiguration().getAccessKey());
        Assertions.assertEquals(KEY_2, a.getConfiguration().getSecretKey());
    }

    @Override
    protected RoutesBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                context.getPropertiesComponent().addInitialProperty("aws.accessKeyId", KEY_1);
                context.getPropertiesComponent().addInitialProperty("aws.secretAccessKey", KEY_2);

                from("kamelet:my-aws-s3-source?accessKey={{aws.accessKeyId}}&bucketNameOrArn=mybucket&region=eu-south-2&autoCreateBucket=false&cheeseKey={{aws.secretAccessKey}}")
                        .autoStartup(false).routeId("myroute")
                        .to("mock:result");
            }
        };
    }
}
