package org.apache.camel.component.file.remote;

import org.apache.camel.spi.SendDynamicAware;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

public class FtpSendDynamicAwareTest extends CamelTestSupport {

    FtpSendDynamicAware dynamicAware;

    @Test
    public void testHttpsUndefinedPortParseUri() {
        this.dynamicAware.setScheme("sftp");
        SendDynamicAware.DynamicAwareEntry entry = new SendDynamicAware.DynamicAwareEntry("https://localhost:80/", null, null, null);
//        String[] result = dynamicAware.asEndpointUri(null, entry, null);
    }
}
