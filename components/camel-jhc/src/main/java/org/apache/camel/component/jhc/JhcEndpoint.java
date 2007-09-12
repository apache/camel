package org.apache.camel.component.jhc;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.BasicHttpParams;

import java.net.URI;

/**
 * Created by IntelliJ IDEA.
 * User: gnodet
 * Date: Sep 7, 2007
 * Time: 8:06:42 PM
 * To change this template use File | Settings | File Templates.
 */
public class JhcEndpoint extends DefaultEndpoint<JhcExchange> {

    private HttpParams params;
    private URI httpUri;

    public JhcEndpoint(String endpointUri, JhcComponent component, URI httpUri) {
        super(endpointUri, component);
        params = new BasicHttpParams(component.getParams());
        this.httpUri = httpUri;
    }

    public HttpParams getParams() {
        return params;
    }

    public void setParams(HttpParams params) {
        this.params = params;
    }

    public URI getHttpUri() {
        return httpUri;
    }

    public void setHttpUri(URI httpUri) {
        this.httpUri = httpUri;
    }

    public String getProtocol() {
        return httpUri.getScheme();
    }

    public String getHost() {
        return httpUri.getHost();
    }

    public int getPort() {
        if (httpUri.getPort() == -1) {
            if ("https".equals(getProtocol())) {
                return 443;
            } else {
                return 80;
            }
        }
        return httpUri.getPort();
    }

    public String getPath() {
        return httpUri.getPath();
    }

    public boolean isSingleton() {
        return true;
    }

    public Producer<JhcExchange> createProducer() throws Exception {
        return new JhcProducer(this);
    }

    public Consumer<JhcExchange> createConsumer(Processor processor) throws Exception {
        return new JhcConsumer(this, processor);
    }
}
