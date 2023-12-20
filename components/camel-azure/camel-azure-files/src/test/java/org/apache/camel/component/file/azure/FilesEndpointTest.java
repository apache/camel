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
package org.apache.camel.component.file.azure;

import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FilesEndpointTest extends CamelTestSupport {

    @Test
    void sasTokenForCopyPastedURIShouldBePreserved() {
        var plainToken
                = "sv=2022-11-02&ss=f&srt=sco&sp=rwdlc&se=2023-05-28T22:50:04Z&st=2023-05-24T14:50:04Z&spr=https&sig=gj%2BUKSiCWSHmcubvGhyJhatkP8hkbXkrmV%2B%2BZme%2BCxI%3D";
        // context while resolving endpoint calls SAS setters with decoded values
        // by observation Camel decoded sig=gj UKSiCWSHmcubvGhyJhatkP8hkbXkrmV  Zme CxI=
        // using URISupport sig=gj+UKSiCWSHmcubvGhyJhatkP8hkbXkrmV++Zme+CxI=
        //  leads to "Signature size is invalid" response from server
        // likely need to post-process replacing + by %2B
        // Camel also sorted params before calling setters
        var endpoint = context.getEndpoint(
                "azure-files://account/share?" + plainToken, FilesEndpoint.class);
        assertEquals(
                plainToken,
                endpoint.getToken().toURIQuery());
    }

}
