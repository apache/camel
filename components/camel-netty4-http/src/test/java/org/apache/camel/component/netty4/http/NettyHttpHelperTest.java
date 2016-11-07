package org.apache.camel.component.netty4.http;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.impl.DefaultExchange;
import org.junit.Test;

import java.net.URISyntaxException;

import static org.junit.Assert.assertEquals;

/**
 *
 */
public class NettyHttpHelperTest {

    @Test
    public void createURLShouldReturnTheHeaderURIIfNotBridgeEndpoint() throws URISyntaxException {
        String url = NettyHttpHelper.createURL(
                createExchangeWithOptionalCamelHttpUriHeader("http://apache.org", null),
                createNettyHttpEndpoint(false, "http://camel.apache.org"));

        assertEquals("http://apache.org", url);
    }

    @Test
    public void createURLShouldReturnTheEndpointURIIfBridgeEndpoint() throws URISyntaxException {
        String url = NettyHttpHelper.createURL(
                createExchangeWithOptionalCamelHttpUriHeader("http://apache.org", null),
                createNettyHttpEndpoint(true, "http://camel.apache.org"));

        assertEquals("http://camel.apache.org", url);
    }

    @Test
    public void createURLShouldReturnTheEndpointURIWithHeaderHttpPathAndAddOneSlash() throws URISyntaxException {
        String url = NettyHttpHelper.createURL(
                createExchangeWithOptionalCamelHttpUriHeader(null, "search"),
                createNettyHttpEndpoint(true, "http://www.google.com"));

        assertEquals("http://www.google.com/search", url);
    }

    @Test
    public void createURLShouldReturnTheEndpointURIWithHeaderHttpPathAndRemoveOneSlash() throws URISyntaxException {
        String url = NettyHttpHelper.createURL(
                createExchangeWithOptionalCamelHttpUriHeader(null, "/search"),
                createNettyHttpEndpoint(true, "http://www.google.com/"));

        assertEquals("http://www.google.com/search", url);
    }

    private Exchange createExchangeWithOptionalCamelHttpUriHeader(String endpointURI, String httpPath) throws URISyntaxException {
        CamelContext context = new DefaultCamelContext();
        DefaultExchange exchange = new DefaultExchange(context);
        Message inMsg = exchange.getIn();
        if (endpointURI != null) {
            inMsg.setHeader(Exchange.HTTP_URI, endpointURI);
        }
        if (httpPath != null) {
            inMsg.setHeader(Exchange.HTTP_PATH, httpPath);
        }

        return exchange;
    }

    private NettyHttpEndpoint createNettyHttpEndpoint(boolean bridgeEndpoint, String endpointURI) throws URISyntaxException {
        NettyHttpConfiguration configuration = new NettyHttpConfiguration();
        configuration.setBridgeEndpoint(bridgeEndpoint);
        return new NettyHttpEndpoint(endpointURI, null, configuration);
    }

}
