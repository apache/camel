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
package org.apache.camel.component.file.remote.integration;

import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.file.remote.FtpConstants;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.health.HealthCheck;
import org.apache.camel.health.HealthCheckHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import static org.awaitility.Awaitility.await;

public class FtpProducerHealthCheckIT extends FtpServerTestSupport {

    private String getFtpUrl() {
        return "ftp://admin@localhost:{{ftp.server.port}}/reply?password=admin";
    }

    @Override
    protected CamelContext createCamelContext() throws Exception {
        CamelContext context = super.createCamelContext();
        HealthCheckHelper.getHealthCheckRepository(context, "producers").setEnabled(true);
        return context;
    }

    @Test
    public void testHealthCheck() throws Exception {
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Bye World");
        mock.expectedHeaderReceived(FtpConstants.FTP_REPLY_CODE, 226);
        mock.expectedHeaderReceived(FtpConstants.FTP_REPLY_STRING, "226 Transfer complete.");

        template.requestBodyAndHeader("direct:start", "Bye World", Exchange.FILE_NAME, "hello.txt");

        MockEndpoint.assertIsSatisfied(context);

        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            Collection<HealthCheck.Result> res = HealthCheckHelper.invokeReadiness(context);
            boolean up = res.stream().allMatch(r -> r.getState().equals(HealthCheck.State.UP));
            Assertions.assertTrue(up, "readiness check");
        });

        // stop FTP server
        service.shutdown();

        // health-check should then become down
        await().atMost(20, TimeUnit.SECONDS).untilAsserted(() -> {
            Collection<HealthCheck.Result> res = HealthCheckHelper.invokeReadiness(context);
            Optional<HealthCheck.Result> hr = res.stream().filter(r -> r.getState().equals(HealthCheck.State.DOWN)).findFirst();
            Assertions.assertTrue(hr.isPresent());
            HealthCheck.Result r = hr.get();
            Assertions.assertEquals(HealthCheck.State.DOWN, r.getState());
            Assertions.assertEquals("FtpProducer is not ready", r.getMessage().get());
            Assertions.assertEquals(200, r.getDetails().get("ftp.code"));
            Assertions.assertEquals("Connection refused", r.getDetails().get("ftp.reason"));
        });

    }

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start").to(getFtpUrl()).to("mock:result");
            }
        };
    }

}
