package org.apache.camel.component.file;

/**
 *
 */
public class GenericFileDefaultBinding implements GenericFileBinding {

    private Object body;

    public Object getBody(GenericFile genericFile) {
        return body;
    }

    public void setBody(GenericFile genericFile, Object body) {
        this.body = body;
    }
}
