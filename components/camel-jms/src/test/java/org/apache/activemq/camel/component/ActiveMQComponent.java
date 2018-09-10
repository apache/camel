package org.apache.activemq.camel.component;

import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.jms.Connection;
import org.apache.activemq.EnhancedConnection;
import org.apache.activemq.Service;
import org.apache.activemq.advisory.DestinationSource;
import org.apache.camel.CamelContext;
import org.apache.camel.component.jms.JmsComponent;
import org.apache.camel.component.jms.JmsConfiguration;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.connection.SingleConnectionFactory;

public class ActiveMQComponent extends JmsComponent {

    private final CopyOnWriteArrayList<SingleConnectionFactory> singleConnectionFactoryList = new CopyOnWriteArrayList<>();
    private final CopyOnWriteArrayList<Service> pooledConnectionFactoryServiceList = new CopyOnWriteArrayList<>();
    private static final transient Logger LOG = LoggerFactory.getLogger(ActiveMQComponent.class);
    private boolean exposeAllQueues;
    private CamelEndpointLoader endpointLoader;
    private EnhancedConnection connection;
    DestinationSource source;

    public static ActiveMQComponent activeMQComponent() {
        return new ActiveMQComponent();
    }

    public static ActiveMQComponent activeMQComponent(String brokerURL) {
        ActiveMQComponent answer = new ActiveMQComponent();
        if (answer.getConfiguration() instanceof ActiveMQConfiguration) {
            ((ActiveMQConfiguration)answer.getConfiguration()).setBrokerURL(brokerURL);
        }

        return answer;
    }

    public ActiveMQComponent() {
    }

    public ActiveMQComponent(CamelContext context) {
        super(context);
    }

    public ActiveMQComponent(ActiveMQConfiguration configuration) {
        this.setConfiguration(configuration);
    }

    public void setBrokerURL(String brokerURL) {
        if (this.getConfiguration() instanceof ActiveMQConfiguration) {
            ((ActiveMQConfiguration)this.getConfiguration()).setBrokerURL(brokerURL);
        }

    }

    /** @deprecated */
    public void setUserName(String userName) {
        this.setUsername(userName);
    }

    public void setTrustAllPackages(boolean trustAllPackages) {
        if (this.getConfiguration() instanceof ActiveMQConfiguration) {
            ((ActiveMQConfiguration)this.getConfiguration()).setTrustAllPackages(trustAllPackages);
        }

    }

    public boolean isExposeAllQueues() {
        return this.exposeAllQueues;
    }

    public void setExposeAllQueues(boolean exposeAllQueues) {
        this.exposeAllQueues = exposeAllQueues;
    }

    public void setUsePooledConnection(boolean usePooledConnection) {
        if (this.getConfiguration() instanceof ActiveMQConfiguration) {
            ((ActiveMQConfiguration)this.getConfiguration()).setUsePooledConnection(usePooledConnection);
        }

    }

    public void setUseSingleConnection(boolean useSingleConnection) {
        if (this.getConfiguration() instanceof ActiveMQConfiguration) {
            ((ActiveMQConfiguration)this.getConfiguration()).setUseSingleConnection(useSingleConnection);
        }

    }

    protected void addPooledConnectionFactoryService(Service pooledConnectionFactoryService) {
        this.pooledConnectionFactoryServiceList.add(pooledConnectionFactoryService);
    }

    protected void addSingleConnectionFactory(SingleConnectionFactory singleConnectionFactory) {
        this.singleConnectionFactoryList.add(singleConnectionFactory);
    }

    protected String convertPathToActualDestination(String path, Map<String, Object> parameters) {
        Map options = IntrospectionSupport.extractProperties(parameters, "destination.");

        String query;
        try {
            query = URISupport.createQueryString(options);
        } catch (URISyntaxException var6) {
            throw ObjectHelper.wrapRuntimeCamelException(var6);
        }

        return ObjectHelper.isNotEmpty(query) ? path + "?" + query : path;
    }

    protected void doStart() throws Exception {
        super.doStart();
        if (this.isExposeAllQueues()) {
            this.createDestinationSource();
            this.endpointLoader = new CamelEndpointLoader(this.getCamelContext(), this.source);
            this.endpointLoader.afterPropertiesSet();
        }

        if (this.getMessageCreatedStrategy() == null) {
            this.setMessageCreatedStrategy(new OriginalDestinationPropagateStrategy());
        }

    }

    protected void createDestinationSource() {
        try {
            if (this.source == null) {
                if (this.connection == null) {
                    Connection value = this.getConfiguration().getConnectionFactory().createConnection();
                    if (!(value instanceof EnhancedConnection)) {
                        throw new IllegalArgumentException("Created JMS Connection is not an EnhancedConnection: " + value);
                    }

                    this.connection = (EnhancedConnection)value;
                    this.connection.start();
                }

                this.source = this.connection.getDestinationSource();
            }
        } catch (Throwable var2) {
            LOG.info("Can't get destination source, endpoint completer will not work", var2);
        }

    }

    protected void doStop() throws Exception {
        if (this.source != null) {
            this.source.stop();
            this.source = null;
        }

        if (this.connection != null) {
            this.connection.close();
            this.connection = null;
        }

        Iterator var1 = this.pooledConnectionFactoryServiceList.iterator();

        while(var1.hasNext()) {
            Service s = (Service)var1.next();
            s.stop();
        }

        this.pooledConnectionFactoryServiceList.clear();
        var1 = this.singleConnectionFactoryList.iterator();

        while(var1.hasNext()) {
            SingleConnectionFactory s = (SingleConnectionFactory)var1.next();
            s.destroy();
        }

        this.singleConnectionFactoryList.clear();
        super.doStop();
    }

    public void setConfiguration(JmsConfiguration configuration) {
        if (configuration instanceof ActiveMQConfiguration) {
            ((ActiveMQConfiguration)configuration).setActiveMQComponent(this);
        }

        super.setConfiguration(configuration);
    }

    protected JmsConfiguration createConfiguration() {
        ActiveMQConfiguration answer = new ActiveMQConfiguration();
        answer.setActiveMQComponent(this);
        return answer;
    }

}
