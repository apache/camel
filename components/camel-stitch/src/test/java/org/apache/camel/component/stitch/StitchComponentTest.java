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
package org.apache.camel.component.stitch;

import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.component.stitch.client.StitchRegion;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StitchComponentTest extends CamelTestSupport {

    @Test
    void testNormalProperties() {
        final String uri = "stitch:my_table?token=mytoken&region=north_america";

        final StitchEndpoint endpoint = context.getEndpoint(uri, StitchEndpoint.class);

        assertEquals("my_table", endpoint.getConfiguration().getTableName());
        assertEquals("mytoken", endpoint.getConfiguration().getToken());
        assertEquals(StitchRegion.NORTH_AMERICA, endpoint.getConfiguration().getRegion());
    }

    @Test
    void testIfNotAllProperties() {
        final String uri2 = "stitch:my_table?region=north_america";

        assertThrows(ResolveEndpointFailedException.class, () -> context.getEndpoint(uri2, StitchEndpoint.class));
    }
}
