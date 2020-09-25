package org.apache.camel.component.log;

import java.util.HashMap;
import java.util.Map;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.ExtendedCamelContext;
import org.apache.camel.spi.EndpointUriFactory;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class LogEndpointUriAssemblerTest extends ContextTestSupport {

    @Test
    public void testLogAssembler() throws Exception {
        Map<String, Object> params = new HashMap<>();
        params.put("loggerName", "foo");
        params.put("groupSize", "123");
        params.put("showExchangePattern", false);
        params.put("logMask", true);

        // should find the source code generated assembler via classpath
        EndpointUriFactory assembler = context.adapt(ExtendedCamelContext.class).getEndpointUriFactory("log");
        Assertions.assertNotNull(assembler);
        boolean generated = assembler instanceof LogEndpointUriAssembler;
        Assertions.assertTrue(generated);

        String uri = assembler.buildUri("log", params);
        Assertions.assertEquals("log:foo?groupSize=123&logMask=true&showExchangePattern=false", uri);
    }
}
