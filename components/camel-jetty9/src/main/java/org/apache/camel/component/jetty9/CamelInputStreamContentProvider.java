package org.apache.camel.component.jetty9;

import java.io.InputStream;

import org.eclipse.jetty.client.util.InputStreamContentProvider;

public class CamelInputStreamContentProvider extends InputStreamContentProvider {

    private int length;

    public CamelInputStreamContentProvider(InputStream stream, int length) {
        super(stream);
        this.length = length;
    }

    @Override
    public long getLength()
    {
        return length;
    }
}
