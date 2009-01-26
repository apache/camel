package org.apache.camel.component.file;

import java.io.File;

/**
 *
 */
public class NewFileBinding implements GenericFileBinding<File> {

    private File body;

    public Object getBody(GenericFile<File> file) {
        // TODO: comment why I do this
        // TODO: consider storing object and only create new if changed
        // TODO: Consider callback from changeName to binding so we change
        // change it at that time
        return new File(file.getAbsoluteFileName());
    }

    public void setBody(GenericFile<File> GenericFile, Object body) {
        // noop
    }
}
