package org.apache.camel.web.resources;

import com.sun.jersey.api.view.ImplicitProduces;

import org.apache.camel.web.connectors.CamelConnection;
import org.apache.camel.web.connectors.CamelDataBean;
import org.apache.camel.web.model.Consumer;
import org.apache.camel.web.model.Consumers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import java.util.List;

/**
 *
 */
@ImplicitProduces(Constants.HTML_MIME_TYPES)
public class ConsumersResource {

    private static final transient Logger LOG = LoggerFactory.getLogger(ConsumersResource.class);

    private CamelConnection camelConnection;

    public ConsumersResource(CamelConnection camelConnection) {
        this.camelConnection = camelConnection;
    }

    /**
     * Returns a list of consumers available in this context
     */
    @GET
    @Produces({MediaType.TEXT_XML, MediaType.APPLICATION_XML, MediaType.APPLICATION_JSON})
    public Consumers getDTO() {
        if(LOG.isDebugEnabled()) {
            LOG.debug("Retrieving consumers.");
        }

        List<CamelDataBean> consumersCamelBeans = camelConnection.getCamelBeans("consumers");
        Consumers consumers = new Consumers();
        consumers.load(consumersCamelBeans);
        return consumers;
    }

    public List<Consumer> getConsumers() {
        return getDTO().getConsumers();
    }

    @Path("{name}/status")
    public ConsumerResource getConsumerStatus(@PathParam("name") String name) {
    	CamelDataBean consumerBean = camelConnection.getCamelBean("consumers", name);
    	
        if (consumerBean != null) {
        	Consumer consumer = new Consumer();
        	consumer.load(consumerBean);
            return new ConsumerResource(consumer, camelConnection);
        } else {
            LOG.warn("No consumer found for name: " + name);
            return null;
        }
    }

}
