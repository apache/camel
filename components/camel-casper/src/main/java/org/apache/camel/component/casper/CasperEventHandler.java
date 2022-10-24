package org.apache.camel.component.casper;

import org.apache.camel.Exchange;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.launchdarkly.eventsource.EventHandler;
import com.launchdarkly.eventsource.MessageEvent;

/**
 * Event handler for the consumer
 * 
 * @author mabahma
 *
 */
public class CasperEventHandler implements EventHandler {

	public static final Logger logger = LoggerFactory.getLogger(CasperEventHandler.class);
	private final CasperConsumer consumer;
	private final CasperEndPoint endpoint;

	public CasperEventHandler(CasperConsumer consumer) {
		super();
		this.consumer = consumer;
		this.endpoint = this.consumer.getEndpoint();
	}

	@Override
	public void onOpen() throws Exception {
		logger.info("The event stream has been opened");
	}

	@Override
	public void onClosed() throws Exception {
		logger.info("The event stream has been closed");
	}

	/**
	 * 
	 */
	@Override
	public void onMessage(String evt, MessageEvent messageEvent) throws Exception {
		JSONObject json = new JSONObject(messageEvent.getData());
		String firstJsonPropertyKey = "";

		if (json.keys().hasNext())
			firstJsonPropertyKey = json.keys().next();
		String event = endpoint.getConfiguration().getEvent().toUpperCase();
		switch (ConsumerEvent.valueOf(event)) {
		case BLOCK_ADDED:
			if (firstJsonPropertyKey.equals("BlockAdded")) {
				logger.debug("received event of type :  BlockAdded");
				processMessage(ConsumerEvent.BLOCK_ADDED, json.getJSONObject(firstJsonPropertyKey));
			}
			break;
		case DEPLOY_PROCESSED:
			if (firstJsonPropertyKey.equals("DeployProcessed")) {
				logger.debug("received event of type :  DeployProcessed");
				processMessage(ConsumerEvent.DEPLOY_PROCESSED, json.getJSONObject(firstJsonPropertyKey));
			}
			break;

		case DEPLOY_ACCEPTED:
			if (firstJsonPropertyKey.equals("DeployAccepted")) {
				logger.debug("received event of type :  DeployAccepted");
				processMessage(ConsumerEvent.DEPLOY_ACCEPTED, json.getJSONObject(firstJsonPropertyKey));
			}
			break;

		case FINALITY_SIGNATURE:
			if (firstJsonPropertyKey.equals("FinalitySignature")) {
				logger.debug("received event of type :  FinalitySignature");
				processMessage(ConsumerEvent.FINALITY_SIGNATURE, json.getJSONObject(firstJsonPropertyKey));
			}
			break;

		case STEP:
			if (firstJsonPropertyKey.equals("Step")) {
				logger.debug("received event of type :  Step");
				processMessage(ConsumerEvent.STEP, json.getJSONObject(firstJsonPropertyKey));
			}
			break;

		case FAULT:
			if (firstJsonPropertyKey.equals("Fault")) {
				logger.debug("received event of type :  Fault");
				processMessage(ConsumerEvent.FAULT, json.getJSONObject(firstJsonPropertyKey));
			}
			break;

		case DEPLOY_EXPIRED:
			if (firstJsonPropertyKey.equals("DeployExpired")) {
				logger.debug("received event of type :  DeployExpired");
				processMessage(ConsumerEvent.DEPLOY_EXPIRED, json.getJSONObject(firstJsonPropertyKey));
			}
			break;

		}
	}

	@Override
	public void onComment(String comment) throws Exception {
		logger.info("Received a comment line from the stream");
	}

	@Override
	public void onError(Throwable t) {
		logger.error("an error occured when connecting to the event stream", t);
	}

	/**
	 * process the message
	 * 
	 * @param operation
	 * @param json      data to process
	 */

	private void processMessage(ConsumerEvent event, JSONObject data) {
		logger.debug("processing message for event: {}", event);

		try {
			Exchange exchange = endpoint.createExchange();
			exchange.getMessage().setBody(data);
			exchange.getIn().setHeader("status", "done");
			exchange.getIn().setHeader("event", event);
			consumer.getProcessor().process(exchange);

		} catch (Exception e) {
			logger.error("Error processing message ", e);
		}
	}

}
