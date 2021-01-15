package org.apache.camel.component.huaweicloud.smn.constants;

import org.junit.Assert;
import org.junit.Test;

public class SmnOperationsTest {
    @Test
    public void testOperationsName(){
        Assert.assertEquals("publishAsTextMessage", SmnOperations.PUBLISH_AS_TEXT_MESSAGE);
        Assert.assertEquals("publishAsTemplatedMessage", SmnOperations.PUBLISH_AS_TEMPLATED_MESSAGE);
    }
}
