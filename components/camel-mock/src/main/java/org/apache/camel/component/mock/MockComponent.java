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
package org.apache.camel.component.mock;

import java.util.Map;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.Processor;
import org.apache.camel.spi.CamelLogger;
import org.apache.camel.spi.ExchangeFormatter;
import org.apache.camel.spi.Metadata;
import org.apache.camel.support.DefaultComponent;
import org.apache.camel.support.processor.DefaultExchangeFormatter;
import org.apache.camel.support.processor.ThroughputLogger;

/**
 * The <a href="http://camel.apache.org/mock.html">Mock Component</a> provides mock endpoints for testing.
 */
@org.apache.camel.spi.annotations.Component("mock")
public class MockComponent extends DefaultComponent {

    @Metadata(label = "producer")
    private boolean log;
    @Metadata(label = "advanced", autowired = true)
    private ExchangeFormatter exchangeFormatter;

    public MockComponent() {
    }

    public MockComponent(CamelContext context) {
        super(context);
    }

    @Override
    protected Endpoint createEndpoint(String uri, String remaining, Map<String, Object> parameters) throws Exception {
        MockEndpoint endpoint = new MockEndpoint(uri, this);
        endpoint.setName(remaining);
        endpoint.setLog(log);

        Integer value = getAndRemoveParameter(parameters, "reportGroup", Integer.class);
        if (value != null) {
            Processor reporter = new ThroughputLogger(new CamelLogger("org.apache.camel.component.mock:" + remaining), value);
            endpoint.setReporter(reporter);
            endpoint.setReportGroup(value);
        }
        setProperties(endpoint, parameters);
        return endpoint;
    }

    @Override
    protected void doInit() throws Exception {
        if (exchangeFormatter == null) {
            DefaultExchangeFormatter def = new DefaultExchangeFormatter();
            def.setShowExchangeId(true);
            def.setShowExchangePattern(false);
            def.setSkipBodyLineSeparator(true);
            def.setShowBody(true);
            def.setShowBodyType(true);
            def.setStyle(DefaultExchangeFormatter.OutputStyle.Default);
            def.setMaxChars(10000);
            exchangeFormatter = def;
        }
    }

    public boolean isLog() {
        return log;
    }

    /**
     * To turn on logging when the mock receives an incoming message.
     * <p/>
     * This will log only one time at INFO level for the incoming message. For more detailed logging then set the logger
     * to DEBUG level for the org.apache.camel.component.mock.MockEndpoint class.
     */
    public void setLog(boolean log) {
        this.log = log;
    }

    public ExchangeFormatter getExchangeFormatter() {
        return exchangeFormatter;
    }

    /**
     * Sets a custom {@link ExchangeFormatter} to convert the Exchange to a String suitable for logging. If not
     * specified, we default to {@link DefaultExchangeFormatter}.
     */
    public void setExchangeFormatter(ExchangeFormatter exchangeFormatter) {
        this.exchangeFormatter = exchangeFormatter;
    }
}
