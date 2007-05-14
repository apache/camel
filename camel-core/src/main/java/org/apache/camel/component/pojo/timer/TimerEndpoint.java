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
package org.apache.camel.component.pojo.timer;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Date;
import java.util.Map;

import org.apache.camel.Consumer;
import org.apache.camel.Processor;
import org.apache.camel.Producer;
import org.apache.camel.RuntimeCamelException;
import org.apache.camel.component.pojo.PojoExchange;
import org.apache.camel.impl.DefaultEndpoint;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.URISupport;

/**
 * Represents a timer endpoint that can generate periodic inbound PojoExchanges. 
 *
 * @version $Revision: 519973 $
 */
public class TimerEndpoint extends DefaultEndpoint<PojoExchange> {
	
    private final TimerComponent component;
	private final String timerName;
    private Date time;
    private long period=-1;
    private long delay=-1;
    private boolean fixedRate;
    private boolean daemon=true;


    public TimerEndpoint(String fullURI, TimerComponent component, String timerPartURI) throws URISyntaxException {
        super(fullURI, component);
        this.component = component;
		
        // Use a URI to extract query so they can be set as properties on the endpoint.
        URI u = new URI(timerPartURI);
        Map options = URISupport.parseParamters(u);
		IntrospectionSupport.setProperties(this, options);
		this.timerName = u.getPath();
		
    }

    public Producer<PojoExchange> createProducer() throws Exception {
    	throw new RuntimeCamelException("Cannot produce to a TimerEndpoint: "+getEndpointUri());
    }

    public Consumer<PojoExchange> createConsumer(Processor processor) throws Exception {    	
        return new TimerConsumer(this, processor);
    }

    public PojoExchange createExchange() {
        return new PojoExchange(getContext());
    }

	public TimerComponent getComponent() {
		return component;
	}

	public String getTimerName() {
		return timerName;
	}

	public boolean isDaemon() {
		return daemon;
	}

	public void setDaemon(boolean daemon) {
		this.daemon = daemon;
	}

	public long getDelay() {
		return delay;
	}

	public void setDelay(long delay) {
		this.delay = delay;
	}

	public boolean isFixedRate() {
		return fixedRate;
	}

	public void setFixedRate(boolean fixedRate) {
		this.fixedRate = fixedRate;
	}

	public long getPeriod() {
		return period;
	}

	public void setPeriod(long period) {
		this.period = period;
	}

	public Date getTime() {
		return time;
	}

	public void setTime(Date time) {
		this.time = time;
	}

	public boolean isSingleton() {
		return true;
	}

}
