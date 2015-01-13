package org.apache.camel.component.jetty9;

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.camel.component.http.HttpBinding;
import org.apache.camel.component.jetty.JettyHttpComponent;
import org.apache.camel.component.jetty.JettyHttpEndpoint;

public class JettyHttpEndpoint9 extends JettyHttpEndpoint {
    public JettyHttpEndpoint9(JettyHttpComponent component, String uri, URI httpURL) throws URISyntaxException {
        super(component, uri, httpURL);
    }
    
    public HttpBinding getBinding() {
        return new AttachmentHttpBinding(this);
    }
}
