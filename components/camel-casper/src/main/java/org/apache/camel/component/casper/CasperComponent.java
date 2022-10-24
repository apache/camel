package org.apache.camel.component.casper;

import java.util.Map;

import org.apache.camel.Endpoint;
import org.apache.camel.spi.Metadata;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Component {@link CasperComponent}.
 */

/**
 * Camel CasperComponent
 * 
 * @author mabahma
 *
 */
@Component("casper")
public class CasperComponent extends DefaultComponent {
	@Metadata(description = "Default configuration")
	private CasperConfiguration configuration;
	public static final  Logger logger = LoggerFactory.getLogger(CasperComponent.class);
	/**
	 * Create Casper endpoint
	 */
	@Override
	protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
		CasperConfiguration conf = configuration != null ? configuration.copy() : new CasperConfiguration();
		CasperEndPoint casper = new CasperEndPoint(uri, remaining, this, conf);
		setProperties(casper, parameters);
		logger.debug("***** CasperComponent create endpoint ");
		return casper;
	}
	public CasperConfiguration getConfiguration() {
		return configuration;
	}
	public void setConfiguration(CasperConfiguration configuration) {
		this.configuration = configuration;
	}
}
