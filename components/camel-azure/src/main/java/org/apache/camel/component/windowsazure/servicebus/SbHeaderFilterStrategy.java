package org.apache.camel.component.windowsazure.servicebus;

import org.apache.camel.impl.DefaultHeaderFilterStrategy;

/**
 * Created by alan on 14/10/16.
 */
public class SbHeaderFilterStrategy extends DefaultHeaderFilterStrategy {
    public SbHeaderFilterStrategy() {
        initialize();
    }

    protected void initialize() {
        // filter headers begin with "Camel" or "org.apache.camel"
        setOutFilterPattern("(Camel|org\\.apache\\.camel)[\\.|a-z|A-z|0-9]*");
    }
}
