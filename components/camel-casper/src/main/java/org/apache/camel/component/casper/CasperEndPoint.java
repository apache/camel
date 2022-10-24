package org.apache.camel.component.casper;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.InvalidPathException;
import java.util.Arrays;

import org.apache.camel.Category;
import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.UriEndpoint;
import org.apache.camel.spi.UriParam;
import org.apache.camel.spi.UriPath;
import org.apache.camel.support.DefaultEndpoint;
import org.apache.commons.validator.routines.UrlValidator;

import com.syntifi.casper.sdk.service.CasperService;

/**
 * Camel casper endpoint : to interract with Casper nodes
 * 
 * @author p35862
 *
 */

@UriEndpoint(firstVersion = "3.14.2", scheme = "casper", title = "Casper Camel Connector", syntax = "casper:nodeUrl", label = "casper", category = { Category.BITCOIN, Category.BLOCKCHAIN,
		Category.API })
public class CasperEndPoint extends DefaultEndpoint {
	/**
	 * CasperService bean : Casper java SDK
	 */
	private CasperService casperService;
	/**
	 * nodeUrl : node address
	 */
	@UriPath(description = "Node URL,  e.g. http://localhost:7777/ for producer, http://localhost:9999/events/main for consumer")
	@Metadata(required = true)
	private String nodeUrl;
	/**
	 * Casper component configuration
	 */
	@UriParam(description = "Casper component configuration")
	private CasperConfiguration configuration;
	/**
	 * CasperEndPoint constructor
	 *
	 * @param uri             : Node Uri
	 * @param remaining       : remaining
	 * @param casperComponent : casperComponent, either producer or consumer
	 * @param configuration   : casperConfiguration
	 * @throws URISyntaxException : URISyntaxException
	 */
	public CasperEndPoint(String uri, String remaining, CasperComponent casperComponent, CasperConfiguration configuration) throws URISyntaxException {
		super(uri, casperComponent);
		this.configuration = configuration;
		validateAndSetURL(remaining);
		// this.nodeUrl = remaining;
	}

	/**
	 * Create a Casper Consumer component
	 *
	 * @param processor : Apache Camel Processor
	 * @return CasperConsumer : Casper Consumer component
	 * @throws Exception : exception
	 */
	@Override
	public Consumer createConsumer(Processor processor) throws Exception {
		URI uri = new URI(nodeUrl);
		String event = configuration.getEvent();
		if (!Arrays.asList(CasperConstants.CONSUMER_PATHS.split(",")).stream().anyMatch(s -> s.equals(uri.getPath())))
			throw new InvalidPathException(uri.getPath(),
					String.format("Invalid path '%s' for Casper Stream event server: expected '/events/main', '/events/deploys' or '/events/sigs ", uri.getPath()));
		if (ConsumerEvent.findByName(event) != null) {
			CasperConsumer consumer = new CasperConsumer(this, processor, configuration);
			configureConsumer(consumer);
			return consumer;
		}
		throw new UnsupportedOperationException(String.format("event '%s' is not supported by casper cosumner", event));
	}
	/**
	 * Create a Casper Producer component
	 * 
	 * @return CasperProducer : Casper Producer component
	 * @throws Exception : exception
	 */
	@Override
	public Producer createProducer() throws Exception {
		String operation = configuration.getOperationOrDefault();
		if (ProducerOperation.findByName(operation) != null)
			return new CasperProducer(this, configuration);
		// Insupported operation
		throw new UnsupportedOperationException(String.format("Operation '%s' not supported by casper producer", operation));
	}
	@Override
	protected void doStart() throws Exception {
		if (configuration.getCasperService() != null) {
			this.casperService = configuration.getCasperService();
		} else {
			URI uri = new URI(nodeUrl);
			this.casperService = CasperService.usingPeer(uri.getHost(), uri.getPort());
		}
		super.doStart();
	}
	/**
	 * Validate node Url
	 * 
	 * @param url : Casper Node url
	 * @throws URISyntaxException : uRISyntaxException
	 */
	public void validateAndSetURL(String url) throws URISyntaxException {
		UrlValidator urlValidator = new UrlValidator(UrlValidator.ALLOW_LOCAL_URLS);
		if (urlValidator.isValid(url)) {
			setNodeUrl(new URI(url).toString());
		} else
			throw new InvalidPathException(url, "Provide a valid \"URL\" for node URL parameter. ");
	}
	public CasperService getCasperService() {
		return casperService;
	}
	public void setCasperService(CasperService casperService) {
		this.casperService = casperService;
	}
	public String getNodeUrl() {
		return nodeUrl;
	}
	public void setNodeUrl(String nodeUrl) {
		this.nodeUrl = nodeUrl;
	}
	public CasperConfiguration getConfiguration() {
		return configuration;
	}
	public void setConfiguration(CasperConfiguration configuration) {
		this.configuration = configuration;
	}
}
