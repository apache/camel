package org.apache.camel.component.jetty9;

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.camel.component.http.HttpBinding;
import org.apache.camel.component.jetty.JettyContentExchange;
import org.apache.camel.component.jetty.JettyHttpComponent;
import org.apache.camel.component.jetty.JettyHttpEndpoint;

public class JettyHttpEndpoint9 extends JettyHttpEndpoint {
    private HttpBinding binding;

    public JettyHttpEndpoint9(JettyHttpComponent component, String uri, URI httpURL) throws URISyntaxException {
        super(component, uri, httpURL);
    }
    
    @Override
    public HttpBinding getBinding() {
        if (this.binding == null) {
            this.binding = new AttachmentHttpBinding(this);
        }
        return this.binding;
    }

    @Override
    public void setBinding(HttpBinding binding) {
        super.setBinding(binding);
        this.binding = binding;
    }
    
    @Override
    public JettyContentExchange createContentExchange() {
        return new JettyContentExchange9();
    } 
}
