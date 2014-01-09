package org.apache.camel.component.schematron.exception;

import org.apache.camel.RuntimeCamelException;

import javax.xml.transform.TransformerConfigurationException;

/**
 * Created by akhettar on 22/12/2013.
 */
public class SchematronConfigException extends RuntimeCamelException {
    public SchematronConfigException(Throwable e) {
        super(e);
    }

    public SchematronConfigException(String message) {
        super(message);
    }
}
