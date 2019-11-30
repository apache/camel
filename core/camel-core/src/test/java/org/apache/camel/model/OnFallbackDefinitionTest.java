package org.apache.camel.model;

import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class OnFallbackDefinitionTest {

    @Test
    public void testLabel() {
        OnFallbackDefinition ofd = new OnFallbackDefinition();
        ofd.addOutput(new ToDefinition("urn:foo1"));
        ofd.addOutput(new ToDefinition("urn:foo2"));
        assertEquals("onFallback[urn:foo1,urn:foo2]", ofd.getLabel());
    }
}
