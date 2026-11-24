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
package org.apache.camel.component.smb;

import java.util.List;

import org.apache.camel.ServiceStatus;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * A starting directory the server does not allow to create (a read-only share, with autoCreate enabled by default): the
 * route starts, and each poll tries to create the directory again.
 */
public class SmbConsumerAutoCreateDeniedIT extends SmbServerTestSupport {

    private final SmbConsumerWarnCapture warnings = new SmbConsumerWarnCapture();

    @BeforeEach
    void captureWarnings() {
        warnings.attach();
    }

    @AfterEach
    void releaseWarnings() {
        warnings.detach();
    }

    @Test
    void routeStartsWhenTheStartingDirectoryCannotBeCreated() throws Exception {
        // the scheduler is not started, so the test runs the polls
        String uri = String.format("smb:%s/data-ro/inbox?username=%s&password=%s&startScheduler=false",
                service.address(), service.userName(), service.password());
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from(uri).routeId("readOnly").to("mock:result");
            }
        });

        assertEquals(ServiceStatus.Started, context.getRouteController().getRouteStatus("readOnly"));
        String denied = "STATUS_ACCESS_DENIED (0xc0000022): Create failed for \\\\" + service.hostname() + "\\data-ro\\inbox";
        String startWarn = "Error auto creating directory: inbox due " + denied + ". This exception is ignored.";
        assertEquals(List.of(startWarn), warnings.messages());

        SmbConsumer consumer = (SmbConsumer) context.getRoute("readOnly").getConsumer();
        assertFalse(consumer.prePollCheck());
        assertFalse(consumer.prePollCheck());

        // both polls are skipped, the WARN is logged once
        String server = "smb://" + service.userName() + "@" + service.address();
        String pollWarn = "Cannot auto-create starting directory inbox at: " + server + " due to: " + denied
                          + ". Will skip this poll.";
        assertEquals(List.of(startWarn, pollWarn), warnings.messages());
    }
}
