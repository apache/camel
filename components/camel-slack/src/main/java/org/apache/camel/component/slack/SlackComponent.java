package org.apache.camel.component.slack;

import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class SlackComponent extends DefaultComponent {

    private static final transient Logger LOG = LoggerFactory.getLogger(SlackComponent.class);

    private String webhookUrl;

    /**
     * Create a slack endpoint
     *
     * @param uri the full URI of the endpoint
     * @param channelName the channel or username that the message should be sent to
     * @param parameters the optional parameters passed in
     * @return the camel endpoint
     * @throws Exception
     */
    @Override
    protected Endpoint createEndpoint(String uri, String channelName, Map<String, Object> parameters) throws Exception {
        Endpoint endpoint = new SlackEndpoint(uri, channelName, this);
        setProperties(endpoint, parameters);
        return endpoint;
    }

    /**
     * Getter for the incoming webhook URL
     *
     * @return String containing the incoming webhook URL
     */
    public String getWebhookUrl() {
        return webhookUrl;
    }

    /**
     * Setter for the incoming webhook URL
     *
     * @param webhookUrl the incoming webhook URL
     */
    public void setWebhookUrl(String webhookUrl) {
        this.webhookUrl = webhookUrl;
    }
}
