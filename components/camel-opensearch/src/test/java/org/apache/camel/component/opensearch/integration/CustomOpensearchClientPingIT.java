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
package org.apache.camel.component.opensearch.integration;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.RETURNS_SMART_NULLS;

import java.io.IOException;
import org.apache.camel.builder.RouteBuilder;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.opensearch.client.opensearch.OpenSearchClient;

class CustomOpensearchClientPingIT extends OpensearchTestSupport {

  private OpenSearchClient openSearchClient = Mockito.mock(OpenSearchClient.class,
      RETURNS_SMART_NULLS);

  @Test
  void testPingDisconnectClient() throws IOException {
    Mockito.when(openSearchClient._transport()).thenReturn(client._transport());
    assertTrue(template().requestBody("direct:disconnectClient", null, Boolean.class));
  }

  @Test
  void testPingDoNotDisconnectClient() throws IOException {
    Mockito.when(openSearchClient._transport()).thenReturn(client._transport());
    assertTrue(template().requestBody("direct:doNotDisconnectClient", null, Boolean.class));
  }

  @Override
  protected RouteBuilder createRouteBuilder() {
    return new RouteBuilder() {
      @Override
      public void configure() {
        bindToRegistry("openSearchClient", openSearchClient);
        from("direct:disconnectClient").to(
            "opensearch://opensearch?operation=Ping&disconnect=true&openSearchClient=#openSearchClient");
        from("direct:doNotDisconnectClient").to(
            "opensearch://opensearch?operation=Ping&disconnect=false&openSearchClient=#openSearchClient");

      }
    };
  }

}
