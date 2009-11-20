/**
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
package org.apache.camel.dataformat.bindy.fix;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import junit.framework.Assert;


import org.apache.camel.dataformat.bindy.BindyKeyValuePairFactory;
import org.apache.camel.dataformat.bindy.model.fix.complex.onetomany.Header;
import org.apache.camel.dataformat.bindy.model.fix.complex.onetomany.Order;
import org.apache.camel.dataformat.bindy.model.fix.complex.onetomany.Trailer;
import org.apache.camel.impl.DefaultPackageScanClassResolver;
import org.apache.camel.spi.PackageScanClassResolver;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.junit.Before;
import org.junit.Test;

public class BindyComplexKeyValuePairStandaloneTest {
	
	private static final transient Log LOG = LogFactory.getLog(BindyComplexKeyValuePairStandaloneTest.class);
	
	protected Map<String, Object> model = new HashMap<String, Object>();
	protected Set<Class> models = new HashSet<Class>();
	BindyKeyValuePairFactory factory;
	int counter;
	
	@Before
	public void init() throws Exception {
		
		// Set factory
		PackageScanClassResolver res = new DefaultPackageScanClassResolver();
		factory = new BindyKeyValuePairFactory(res, "org.apache.camel.dataformat.bindy.model.fix.complex.onetomany");
		
		// Set model class
		models.add(org.apache.camel.dataformat.bindy.model.fix.complex.onetomany.Order.class);
		models.add(org.apache.camel.dataformat.bindy.model.fix.complex.onetomany.Header.class);
		// f.models.add(org.apache.camel.dataformat.bindy.model.fix.complex.onetomany.Security.class);
		models.add(org.apache.camel.dataformat.bindy.model.fix.complex.onetomany.Trailer.class);
		
		// Init model
		model.put("org.apache.camel.dataformat.bindy.model.fix.complex.onetomany.Order", new Order());
		model.put("org.apache.camel.dataformat.bindy.model.fix.complex.onetomany.Header", new Header());
		model.put("org.apache.camel.dataformat.bindy.model.fix.complex.onetomany.Trailer", new Trailer());
		
		// set counter = 1
		counter = 1;
	}
	
    @Test
	public void testOneGroupMessage() throws Exception {

		String message = "8=FIX 4.19=2034=135=049=INVMGR56=BRKR"
				+ "1=BE.CHM.00111=CHM0001-0158=this is a camel - bindy test" 
				+ "22=448=BE000124567854=1"
				+ "10=220";

		List<String> data = Arrays.asList(message.split("\\u0001"));

		factory.bind(data, model, counter);
		
		LOG.info(">>> Model : " + model.toString());
		
		Assert.assertNotNull(model);

	}
    
    @Test
	public void testSeveralGroupMessage() throws Exception {

		String message = "8=FIX 4.19=2034=135=049=INVMGR56=BRKR"
				+ "1=BE.CHM.00111=CHM0001-0158=this is a camel - bindy test" 
				+ "22=448=BE000124567854=1"
				+ "22=548=BE000987654354=2" 
				+ "22=648=BE000999999954=3" 
				+ "10=220";

		List<String> data = Arrays.asList(message.split("\\u0001"));

		factory.bind(data, model, counter);
		
		LOG.info(">>> Model : " + model.toString());
		
		Assert.assertNotNull(model);

	}
    
    @Test
	public void testNoGroupMessage() throws Exception {

		String message = "8=FIX 4.19=2034=135=049=INVMGR56=BRKR"
				+ "1=BE.CHM.00111=CHM0001-0158=this is a camel - bindy test" 
				+ "10=220";

		List<String> data = Arrays.asList(message.split("\\u0001"));

		factory.bind(data, model, counter);
		
		LOG.info(">>> Model : " + model.toString());
		
		Assert.assertNotNull(model);

	}
    
    
    
/*    public List<Map<String, Object>> generateModel() {
        Map<String, Object> model = new HashMap<String, Object>();
    	List<Map<String, Object>> models = new ArrayList<Map<String, Object>>();
    	
        Header header = new Header();
        header.setBeginString("FIX 4.1");
        header.setBodyLength(20);
        header.setMsgSeqNum(1);
        header.setMsgType("0");
        header.setSendCompId("INVMGR");
        header.setTargetCompId("BRKR");
        
        Trailer trailer = new Trailer();
        trailer.setCheckSum(220); 
        
        Order order = new Order();
        order.setAccount("BE.CHM.001");
        order.setClOrdId("CHM0001-01");
        order.setText("this is a camel - bindy test");
        order.setIDSource("4");
        order.setSecurityId("BE0001245678");
        order.setSide("1");
        
        order.setHeader(header);
        order.setTrailer(trailer);
        
        model.put(order.getClass().getName(), order);
        model.put(header.getClass().getName(), header);
        model.put(trailer.getClass().getName(), trailer);
 
        models.add(model);
        return models;
    }*/

}
