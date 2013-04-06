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
package org.apache.camel.component.xquery;

import javax.naming.Context;

import org.apache.camel.Handler;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.apache.camel.util.jndi.JndiContext;
import org.junit.Test;

public class BeanWithXQueryInjectionUsingHeaderValueTest extends CamelTestSupport {
    protected MyBean myBean = new MyBean();

    @Test
    public void testConstantXPathHeaders() throws Exception {
        template.sendBodyAndHeader("bean:myBean", "<response>OK</response>",
                                   "invoiceDetails", "<invoice><person><name>Alan</name><date>26/08/2012</date></person></invoice>");
       
        assertEquals("bean response:  " + myBean, "OK", myBean.response);
        assertEquals("bean userName: " + myBean, "Alan", myBean.userName);
        assertEquals("bean date:  " + myBean, "26/08/2012", myBean.date);
    }
    
    @Override
    protected Context createJndiContext() throws Exception {
        JndiContext answer = new JndiContext();
        answer.bind("myBean", myBean);
        return answer;
    }

    public static class MyBean {
        public String userName;
        public String date;
        public String response;

        @Handler
        public void handler(@XQuery("/response") String response,
                            @XQuery(headerName = "invoiceDetails", value = "/invoice/person/name") String userName,
                            @XQuery(headerName = "invoiceDetails", value = "/invoice/person/date") String date) {
            this.response = response;
            this.userName = userName;
            this.date = date;
        }
    }

}
