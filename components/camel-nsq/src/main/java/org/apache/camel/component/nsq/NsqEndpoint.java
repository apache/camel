package org.apache.camel.component.nsq;

import com.github.brainlag.nsq.NSQConfig;
import io.netty.handler.ssl.JdkSslContext;
import io.netty.handler.ssl.SslContext;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.util.ObjectHelper;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.concurrent.ExecutorService;

/**
 * Represents a nsq endpoint.
 */
@UriEndpoint(firstVersion = "2.22.0", scheme = "nsq", title = "nsq", syntax="nsq:lookupServer",
             consumerClass = NsqConsumer.class, label = "messaging")
public class NsqEndpoint extends DefaultEndpoint {

    @UriParam
    private NsqConfiguration configuration;

    public NsqEndpoint(String uri, NsqComponent component, NsqConfiguration configuration) {
        super(uri, component);
        this.configuration = configuration;
    }
    public Producer createProducer() throws Exception {
        return new NsqProducer(this);
    }

    public Consumer createConsumer(Processor processor) throws Exception {
        if (ObjectHelper.isEmpty(configuration.getTopic())) {
            throw new RuntimeCamelException("Missing required endpoint configuration: topic must be defined for NSQ consumer");
        }
        return new NsqConsumer(this, processor);
    }

    public ExecutorService createExecutor() {
        return getCamelContext().getExecutorServiceManager().newFixedThreadPool(this, "NsqTopic[" + configuration.getTopic() + "]", configuration.getPoolSize());
    }

    public boolean isSingleton() {
        return true;
    }

    public NsqConfiguration getNsqConfiguration() {
        return configuration;
    }

    public NSQConfig getNsqConfig() throws GeneralSecurityException, IOException {
        NSQConfig nsqConfig = new NSQConfig();

        if (getNsqConfiguration().getSslContextParameters() != null && getNsqConfiguration().isSecure()) {
            SslContext sslContext = new JdkSslContext(getNsqConfiguration().getSslContextParameters().createSSLContext(getCamelContext()), true, null);
            nsqConfig.setSslContext(sslContext);
        }

        if (configuration.getUserAgent() != null && !configuration.getUserAgent().isEmpty()) {
            nsqConfig.setUserAgent(configuration.getUserAgent());
        }

        if (configuration.getMessageTimeout() > -1) {
            nsqConfig.setMsgTimeout((int) configuration.getMessageTimeout());
        }

        return nsqConfig;
    }
}
