package org.apache.camel.component.casper;
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
