package org.apache.camel.component.schematron.exception;

import org.apache.camel.RuntimeCamelException;

/**
 * SchematronValidationException is thrown if option is set to true.
 *
 * Created by akhettar on 25/12/2013.
 */
public class SchematronValidationException extends RuntimeCamelException {


    public SchematronValidationException(final String message, Throwable e)
    {
        super(message,e);
    }
}
