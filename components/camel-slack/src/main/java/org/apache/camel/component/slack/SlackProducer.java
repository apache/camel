package org.apache.camel.component.slack;

import org.apache.camel.CamelException;
import org.apache.camel.Exchange;
import org.apache.camel.component.slack.helper.SlackMessage;
import org.apache.camel.impl.DefaultProducer;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.HttpClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SlackProducer extends DefaultProducer {

    private static final transient Logger LOG = LoggerFactory.getLogger(SlackProducer.class);

    private SlackEndpoint slackEndpoint;

    /**
     * Constuctor
     *
     * @param endpoint a SlackEndpoint
     */
    public SlackProducer(SlackEndpoint endpoint) {
        super(endpoint);
        this.slackEndpoint = endpoint;
    }

    @Override
    public void process(Exchange exchange) throws Exception {

        // Create an HttpClient and Post object
        HttpClient client = HttpClientBuilder.create().build();
        HttpPost httpPost = new HttpPost(slackEndpoint.getWebhookUrl());

        // Build Helper object
        SlackMessage slackMessage = new SlackMessage();
        slackMessage.setText(exchange.getIn().getBody(String.class));
        slackMessage.setChannel(slackEndpoint.getChannel());
        slackMessage.setUsername(slackEndpoint.getUsername());
        slackMessage.setIconUrl(slackEndpoint.getIconUrl());
        slackMessage.setIconEmoji(slackEndpoint.getIconEmoji());

        // Set the post body
        StringEntity body = new StringEntity(slackMessage.toString());

        // Do the post
        httpPost.setEntity(body);

        HttpResponse response = client.execute(httpPost);

        if (response.getStatusLine().getStatusCode() != 200) {
            LOG.error("Error POSTing to Slack API: " + response.toString());
            throw new CamelException("Error POSTing to Slack API: " + response.toString());
        }
    }
}
