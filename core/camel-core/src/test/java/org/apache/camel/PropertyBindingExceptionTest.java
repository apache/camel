package org.apache.camel;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class PropertyBindingExceptionTest {
    public static final String EXPECTED_EXCEPTION_MESSAGE = "Error binding property (prefix.property=value) with name: property";

    @Test
    public void exceptionMessageTest(){
        PropertyBindingException pbe = new PropertyBindingException(new Object(), "property", "value", "prefix", "property", new Throwable("The casue!"));
        assertTrue("PropertyBindingException message should start with [" + EXPECTED_EXCEPTION_MESSAGE + "] while is [" + pbe.getMessage() + "] instead.",
                pbe.getMessage().startsWith(EXPECTED_EXCEPTION_MESSAGE));

        pbe = new PropertyBindingException(new Object(), "property", "value", "prefix.", "property", new Throwable("The casue!"));
        assertTrue("PropertyBindingException message should start with [" + EXPECTED_EXCEPTION_MESSAGE + "] while is [" + pbe.getMessage() + "] instead.",
                pbe.getMessage().startsWith(EXPECTED_EXCEPTION_MESSAGE));
    }
}
