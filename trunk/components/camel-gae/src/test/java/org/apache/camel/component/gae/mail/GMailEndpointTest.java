/**
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
package org.apache.camel.component.gae.mail;

import org.junit.Test;

import static org.apache.camel.component.gae.mail.GMailTestUtils.createEndpoint;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class GMailEndpointTest {

    private static final String AMP = "&";
    
    @Test
    public void testPropertiesCustom() throws Exception {
        StringBuilder buffer = new StringBuilder("gmail:user1@gmail.com")
            .append("?").append("subject=test")
            .append(AMP).append("to=user2@gmail.com")
            .append(AMP).append("cc=user3@gmail.com")
            .append(AMP).append("bcc=user4@gmail.com")
            .append(AMP).append("outboundBindingRef=#customBinding");
        GMailEndpoint endpoint = createEndpoint(buffer.toString());
        assertEquals("test", endpoint.getSubject());
        assertEquals("user1@gmail.com", endpoint.getSender());
        assertEquals("user2@gmail.com", endpoint.getTo());
        assertEquals("user3@gmail.com", endpoint.getCc());
        assertEquals("user4@gmail.com", endpoint.getBcc());
        assertFalse(endpoint.getOutboundBinding().getClass().equals(GMailBinding.class));
        assertTrue(endpoint.getOutboundBinding() instanceof GMailBinding);
    }

}
