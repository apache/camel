package org.apache.camel.component.dns.policy;

import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class DnsActivationTest {
    @Test
    public void testDnsActivation() throws Exception {

        DnsActivation dnsActivationActive = new DnsActivation("localhost", Arrays.asList("127.0.0.1"));
        assertTrue(dnsActivationActive.isActive());

        DnsActivation dnsActivationInactive = new DnsActivation("localhost", Arrays.asList("127.0.0.2"));
        assertFalse(dnsActivationInactive.isActive());

    }
}
