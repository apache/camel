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
