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

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.dataformat.bindy.BindyAbstractFactory;
import org.apache.camel.dataformat.bindy.kvp.BindyKeyValuePairDataFormat;
import org.apache.camel.dataformat.bindy.model.fix.complex.onetomany.Header;
import org.apache.camel.dataformat.bindy.model.fix.complex.onetomany.Order;
import org.apache.camel.dataformat.bindy.model.fix.complex.onetomany.Trailer;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BindyComplexKeyValuePairStandaloneTest {

    private static final Logger LOG = LoggerFactory.getLogger(BindyComplexKeyValuePairStandaloneTest.class);

    protected Map<String, Object> model = new HashMap<String, Object>();
    protected Set<Class<?>> models = new HashSet<Class<?>>();
    BindyAbstractFactory factory;
    int counter;

    @Before
    public void init() throws Exception {

        // Set factory
        BindyKeyValuePairDataFormat dataFormat = new BindyKeyValuePairDataFormat(org.apache.camel.dataformat.bindy.model.fix.complex.onetomany.Order.class);
        factory = dataFormat.getFactory();

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
                + "10=220"
                + "777=22-06-2013 12:21:11";

        List<String> data = Arrays.asList(message.split("\\u0001"));

        CamelContext camelContext = new DefaultCamelContext();
        factory.bind(camelContext, data, model, counter);

        LOG.info(">>> Model : " + model.toString());

        Assert.assertNotNull(model);

    }

    @Test
    public void testSeveralGroupMessage() throws Exception {

        String message = "8=FIX 4.19=2034=135=049=INVMGR56=BRKR"
                + "1=BE.CHM.00111=CHM0001-0158=this is a camel - bindy test" + "22=448=BE000124567854=1"
                + "22=548=BE000987654354=2" + "22=648=BE000999999954=3" + "10=220"
                + "777=22-06-2013 12:21:11";

        List<String> data = Arrays.asList(message.split("\\u0001"));

        CamelContext camelContext = new DefaultCamelContext();
        factory.bind(camelContext, data, model, counter);

        LOG.info(">>> Model : " + model.toString());

        Assert.assertNotNull(model);

    }

    @Test
    public void testNoGroupMessage() throws Exception {

        String message = "8=FIX 4.19=2034=135=049=INVMGR56=BRKR"
                + "1=BE.CHM.00111=CHM0001-0158=this is a camel - bindy test"
                + "10=220"
                + "777=22-06-2013 12:21:11";

        List<String> data = Arrays.asList(message.split("\\u0001"));

        CamelContext camelContext = new DefaultCamelContext();
        factory.bind(camelContext, data, model, counter);

        LOG.info(">>> Model : " + model.toString());

        Assert.assertNotNull(model);

    }

}
