package org.apache.camel.component.hipchat;

import java.io.IOException;

import org.apache.camel.CamelContext;
import org.apache.camel.EndpointInject;
import org.apache.camel.ProducerTemplate;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.impl.JndiRegistry;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.conn.ClientConnectionManager;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.params.HttpParams;
import org.apache.http.protocol.HttpContext;
import org.junit.Test;

public class HipchatComponentCustomHttpClientTest extends CamelTestSupport {

    @EndpointInject(uri = "hipchat:http://api.hipchat.com?httpClient=#myHttpClient&authToken=anything&consumeUsers=@AUser")
    private HipchatEndpoint hipchatEndpoint;
    
    @Test
    public void ensureCustomHttpClientIsDefined() {
        HttpClient httpClient = hipchatEndpoint.getConfiguration().getHttpClient();
        assertNotNull(httpClient);
        assertIsInstanceOf(MyCustomHttpClient.class, httpClient);
    }

    @Override
    protected JndiRegistry createRegistry() throws Exception {
        JndiRegistry reg = super.createRegistry();
        reg.bind("myHttpClient", new MyCustomHttpClient());
        return reg;
    }

    @Override
    protected RouteBuilder createRouteBuilder() throws Exception {
        return new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                    .to("hipchat:http://api.hipchat.com?httpClient=#myHttpClient&authToken=anything&consumeUsers=@AUser")
                    .to("mock:result");
            }
        };
    }

    public static class MyCustomHttpClient extends CloseableHttpClient {

        private final CloseableHttpClient innerHttpClient;

        public MyCustomHttpClient() {
            this.innerHttpClient = HttpClientBuilder.create().build();
        }

        @Override
        public HttpParams getParams() {
            return innerHttpClient.getParams();
        }

        @Override
        public ClientConnectionManager getConnectionManager() {
            return innerHttpClient.getConnectionManager();
        }

        @Override
        public void close() throws IOException {
            innerHttpClient.close();
        }

        @Override
        protected CloseableHttpResponse doExecute(HttpHost target, HttpRequest request, HttpContext context) throws IOException, ClientProtocolException {
            return innerHttpClient.execute(target, request, context);
        }
    }

}
