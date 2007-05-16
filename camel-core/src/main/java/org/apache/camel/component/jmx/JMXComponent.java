/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.jmx;

import java.util.Map;
import javax.management.MBeanServer;
import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.impl.DefaultComponent;
import org.apache.camel.util.IntrospectionSupport;

/**
 * The <a href="http://activemq.apache.org/camel/jmx.html">JMX Component</a> for monitoring jmx attributes
 *
 * @version $Revision: 523772 $
 */
public class JMXComponent extends DefaultComponent <JMXExchange>{
	private MBeanServer mbeanServer;
	
    public JMXComponent() {
    }

    public JMXComponent(CamelContext context) {
        super(context);
    }

    protected Endpoint<JMXExchange> createEndpoint(String uri, String remaining, Map parameters) throws Exception {
       
        JMXEndpoint result = new JMXEndpoint(remaining, this);
        IntrospectionSupport.setProperties(result, parameters);
        result.setMbeanServer(getMbeanServer());
        return result;
    }

	
    public MBeanServer getMbeanServer(){
    	return mbeanServer;
    }

	
    public void setMbeanServer(MBeanServer mbeanServer){
    	this.mbeanServer=mbeanServer;
    }
}
