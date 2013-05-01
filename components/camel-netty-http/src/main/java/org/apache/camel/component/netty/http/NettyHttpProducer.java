package org.apache.camel.component.netty.http;

import org.apache.camel.Exchange;
import org.apache.camel.component.netty.NettyConfiguration;
import org.apache.camel.component.netty.NettyProducer;

/**
 * HTTP based {@link NettyProducer}.
 */
public class NettyHttpProducer extends NettyProducer {

    public NettyHttpProducer(NettyHttpEndpoint nettyEndpoint, NettyConfiguration configuration) {
        super(nettyEndpoint, configuration);
    }

    @Override
    public NettyHttpEndpoint getEndpoint() {
        return (NettyHttpEndpoint) super.getEndpoint();
    }

    @Override
    protected Object getRequestBody(Exchange exchange) throws Exception {
        String uri = getEndpoint().getEndpointUri();
        if (exchange.hasOut()) {
            return getEndpoint().getNettyHttpBinding().toNettyRequest(exchange.getOut(), uri);
        } else {
            return getEndpoint().getNettyHttpBinding().toNettyRequest(exchange.getIn(), uri);
        }
    }
}
