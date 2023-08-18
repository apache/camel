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
package org.apache.camel.component.aws2.timestream;

import org.apache.camel.component.aws2.timestream.query.Timestream2QueryEndpoint;
import org.apache.camel.component.aws2.timestream.write.Timestream2WriteEndpoint;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.Protocol;
import software.amazon.awssdk.regions.Region;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class Timestream2ComponentConfigurationTest extends CamelTestSupport {

    @Test
    public void createEndpointWithComponentElements() throws Exception {
        Timestream2Component component = context.getComponent("aws2-timestream", Timestream2Component.class);
        component.getConfiguration().setAccessKey("XXX");
        component.getConfiguration().setSecretKey("YYY");
        Timestream2WriteEndpoint writeEndpoint
                = (Timestream2WriteEndpoint) component.createEndpoint("aws2-timestream://write:label");
        Timestream2QueryEndpoint queryEndpoint
                = (Timestream2QueryEndpoint) component.createEndpoint("aws2-timestream://query:label");

        assertEquals("XXX", writeEndpoint.getConfiguration().getAccessKey());
        assertEquals("YYY", writeEndpoint.getConfiguration().getSecretKey());
        assertEquals("XXX", queryEndpoint.getConfiguration().getAccessKey());
        assertEquals("YYY", queryEndpoint.getConfiguration().getSecretKey());
    }

    @Test
    public void createEndpointWithComponentAndEndpointElements() throws Exception {
        Timestream2Component component = context.getComponent("aws2-timestream", Timestream2Component.class);
        component.getConfiguration().setAccessKey("XXX");
        component.getConfiguration().setSecretKey("YYY");
        component.getConfiguration().setRegion(Region.US_WEST_1.toString());
        Timestream2WriteEndpoint writeEndpoint
                = (Timestream2WriteEndpoint) component
                        .createEndpoint("aws2-timestream://write:label?accessKey=xxxxxx&secretKey=yyyyy&region=US_EAST_1");

        Timestream2QueryEndpoint queryEndpoint
                = (Timestream2QueryEndpoint) component
                        .createEndpoint("aws2-timestream://query:label?accessKey=xxxxxx&secretKey=yyyyy&region=US_EAST_1");

        assertEquals("xxxxxx", writeEndpoint.getConfiguration().getAccessKey());
        assertEquals("yyyyy", writeEndpoint.getConfiguration().getSecretKey());
        assertEquals("US_EAST_1", writeEndpoint.getConfiguration().getRegion());

        assertEquals("xxxxxx", queryEndpoint.getConfiguration().getAccessKey());
        assertEquals("yyyyy", queryEndpoint.getConfiguration().getSecretKey());
        assertEquals("US_EAST_1", queryEndpoint.getConfiguration().getRegion());
    }

    @Test
    public void createEndpointWithComponentEndpointElementsAndProxy() throws Exception {
        Timestream2Component component = context.getComponent("aws2-timestream", Timestream2Component.class);
        component.getConfiguration().setAccessKey("XXX");
        component.getConfiguration().setSecretKey("YYY");
        component.getConfiguration().setRegion(Region.US_WEST_1.toString());
        Timestream2WriteEndpoint writeEndpoint = (Timestream2WriteEndpoint) component
                .createEndpoint(
                        "aws2-timestream://write:label?accessKey=xxxxxx&secretKey=yyyyy&region=US_EAST_1&proxyHost=localhost&proxyPort=9000&proxyProtocol=HTTP");

        Timestream2QueryEndpoint queryEndpoint = (Timestream2QueryEndpoint) component
                .createEndpoint(
                        "aws2-timestream://query:label?accessKey=xxxxxx&secretKey=yyyyy&region=US_EAST_1&proxyHost=localhost&proxyPort=9000&proxyProtocol=HTTP");

        assertEquals("xxxxxx", writeEndpoint.getConfiguration().getAccessKey());
        assertEquals("yyyyy", writeEndpoint.getConfiguration().getSecretKey());
        assertEquals("US_EAST_1", writeEndpoint.getConfiguration().getRegion());
        assertEquals(Protocol.HTTP, writeEndpoint.getConfiguration().getProxyProtocol());
        assertEquals("localhost", writeEndpoint.getConfiguration().getProxyHost());
        assertEquals(Integer.valueOf(9000), writeEndpoint.getConfiguration().getProxyPort());

        assertEquals("xxxxxx", queryEndpoint.getConfiguration().getAccessKey());
        assertEquals("yyyyy", queryEndpoint.getConfiguration().getSecretKey());
        assertEquals("US_EAST_1", queryEndpoint.getConfiguration().getRegion());
        assertEquals(Protocol.HTTP, queryEndpoint.getConfiguration().getProxyProtocol());
        assertEquals("localhost", queryEndpoint.getConfiguration().getProxyHost());
        assertEquals(Integer.valueOf(9000), queryEndpoint.getConfiguration().getProxyPort());
    }

    @Test
    public void createEndpointWithOverrideEndpoint() throws Exception {
        Timestream2Component component = context.getComponent("aws2-timestream", Timestream2Component.class);
        component.getConfiguration().setAccessKey("XXX");
        component.getConfiguration().setSecretKey("YYY");
        component.getConfiguration().setRegion(Region.US_WEST_1.toString());
        Timestream2WriteEndpoint writeEndpoint
                = (Timestream2WriteEndpoint) component.createEndpoint(
                        "aws2-timestream://write:label?accessKey=xxxxxx&secretKey=yyyyy&region=US_EAST_1&overrideEndpoint=true&uriEndpointOverride=http://localhost:9090");

        Timestream2QueryEndpoint queryEndpoint
                = (Timestream2QueryEndpoint) component.createEndpoint(
                        "aws2-timestream://query:label?accessKey=xxxxxx&secretKey=yyyyy&region=US_EAST_1&overrideEndpoint=true&uriEndpointOverride=http://localhost:9090");

        assertEquals("xxxxxx", writeEndpoint.getConfiguration().getAccessKey());
        assertEquals("yyyyy", writeEndpoint.getConfiguration().getSecretKey());
        assertEquals("US_EAST_1", writeEndpoint.getConfiguration().getRegion());
        assertTrue(writeEndpoint.getConfiguration().isOverrideEndpoint());
        assertEquals("http://localhost:9090", writeEndpoint.getConfiguration().getUriEndpointOverride());

        assertEquals("xxxxxx", queryEndpoint.getConfiguration().getAccessKey());
        assertEquals("yyyyy", queryEndpoint.getConfiguration().getSecretKey());
        assertEquals("US_EAST_1", queryEndpoint.getConfiguration().getRegion());
        assertTrue(writeEndpoint.getConfiguration().isOverrideEndpoint());
        assertEquals("http://localhost:9090", queryEndpoint.getConfiguration().getUriEndpointOverride());

    }
}
