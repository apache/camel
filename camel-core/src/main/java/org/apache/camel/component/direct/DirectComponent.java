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
package org.apache.camel.component.direct;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

import org.apache.camel.CamelContext;
import org.apache.camel.Component;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.util.IntrospectionSupport;
import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.URISupport;

/**
 * Represents the component that manages {@link DirectEndpoint}.  It holds the 
 * list of named direct endpoints.
 *
 * @org.apache.xbean.XBean
 * @version $Revision: 519973 $
 */
public class DirectComponent<E extends Exchange> implements Component<E> {

	private CamelContext context;

	public CamelContext getCamelContext() {
		return context;
	}

	public ScheduledExecutorService getExecutorService() {
		return null;
	}

	public Endpoint<E> createEndpoint(String uri) throws Exception {

        ObjectHelper.notNull(getCamelContext(), "camelContext");        
        URI u = new URI(uri);
        Map parameters = URISupport.parseParamters(u);

        Endpoint<E> endpoint = new DirectEndpoint<E>(uri,this);
        if (parameters != null) {
            IntrospectionSupport.setProperties(endpoint, parameters);
        }
        return endpoint;
	}

	public void setCamelContext(CamelContext context) {
		this.context = context;
	}	

}
