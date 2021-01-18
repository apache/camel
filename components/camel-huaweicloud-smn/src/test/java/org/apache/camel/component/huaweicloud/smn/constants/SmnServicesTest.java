package org.apache.camel.component.huaweicloud.smn.constants;

import org.junit.Assert;
import org.junit.Test;

public class SmnServicesTest {
    @Test
    public void testSmnServicesName() {
        Assert.assertEquals("publishMessageService", SmnServices.PUBLISH_MESSAGE);
    }
}
