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
package org.apache.camel.component.dns.policy;

import java.util.Arrays;

import org.junit.Ignore;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@Ignore("Manual test as cannot run reliable on all platforms")
public class DnsActivationTest {

    @Test
    public void testDnsActivation() throws Exception {
        DnsActivation dnsActivationActive = new DnsActivation("localhost", Arrays.asList("127.0.0.1"));
        assertTrue(dnsActivationActive.isActive());

        DnsActivation dnsActivationInactive = new DnsActivation("localhost", Arrays.asList("127.0.0.2"));
        assertFalse(dnsActivationInactive.isActive());
    }
}
