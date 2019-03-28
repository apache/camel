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
package org.apache.camel.component.dropbox;

import org.apache.camel.Consumer;
import org.apache.camel.Endpoint;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.dropbox.integration.consumer.DropboxScheduledPollGetConsumer;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Assert;
import org.junit.Test;

public class DropboxConsumerTest extends CamelTestSupport {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("dropbox://get?accessToken=accessToken&remotePath=/path").to("mock:test1");
                
                from("dropbox://get?accessToken=accessToken&remotePath=/path with spaces/file").to("mock:test2");
            }
        };
    }

    @Test
    public void shouldCreateGetConsumer() throws Exception {
        // Given
        Endpoint dropboxEndpoint1 = context.getEndpoint("dropbox://get?accessToken=accessToken&remotePath=/path");

        // When
        Consumer consumer1 = dropboxEndpoint1.createConsumer(null);

        // Then
        Assert.assertTrue(consumer1 instanceof DropboxScheduledPollGetConsumer);
        
        // Given
        Endpoint dropboxEndpoint2 = context.getEndpoint("dropbox://get?accessToken=accessToken&remotePath=/path with spaces/file");

        // When
        Consumer consumer2 = dropboxEndpoint2.createConsumer(null);

        // Then
        Assert.assertTrue(consumer2 instanceof DropboxScheduledPollGetConsumer);
    }

}