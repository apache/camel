package org.apache.camel.component.huaweicloud.smn.constants;

import org.junit.Assert;
import org.junit.Test;

public class SmnConstantsTest {
    @Test
    public void testConstants(){
        Assert.assertEquals("urn:smn:%s:%s:%s", SmnConstants.TOPIC_URN_FORMAT);
    }
}
