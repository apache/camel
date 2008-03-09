package org.apache.camel.component.mina;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.ResolveEndpointFailedException;

/**
 * For testing various minor holes that hasn't been covered by other unit tests.
 *
 * @version $Revision$
 */
public class MinaComponentTest extends ContextTestSupport {

    public void testUnknownProtocol() {
        try {
            template.setDefaultEndpointUri("mina:xxx://localhost:8080");
            template.sendBody("mina:xxx://localhost:8080");
            fail("Should have thrown a ResolveEndpointFailedException");
        } catch (ResolveEndpointFailedException e) {
            assertTrue("Should be an IAE exception", e.getCause() instanceof IllegalArgumentException);
            assertEquals("Unrecognised MINA protocol: xxx for uri: mina:xxx://localhost:8080", e.getCause().getMessage());
        }
    }

}
