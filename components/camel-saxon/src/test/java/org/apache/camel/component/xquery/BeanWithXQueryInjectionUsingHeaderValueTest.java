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
package org.apache.camel.component.xquery;

import org.apache.camel.Handler;
import org.apache.camel.spi.Registry;
import org.apache.camel.test.junit5.CamelTestSupport;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class BeanWithXQueryInjectionUsingHeaderValueTest extends CamelTestSupport {
    protected MyBean myBean = new MyBean();

    @Test
    public void testConstantXPathHeaders() {
        template.sendBodyAndHeader("bean:myBean", "<response>OK</response>",
                "invoiceDetails", "<invoice><person><name>Alan</name><date>26/08/2012</date></person></invoice>");

        assertEquals("OK", myBean.response, "bean response:  " + myBean);
        assertEquals("Alan", myBean.userName, "bean userName: " + myBean);
        assertEquals("26/08/2012", myBean.date, "bean date:  " + myBean);
    }

    @Override
    protected void bindToRegistry(Registry registry) {
        registry.bind("myBean", myBean);
    }

    public static class MyBean {
        public String userName;
        public String date;
        public String response;

        @Handler
        public void handler(
                @XQuery("/response") String response,
                @XQuery(source = "header:invoiceDetails", value = "/invoice/person/name") String userName,
                @XQuery(source = "header:invoiceDetails", value = "/invoice/person/date") String date) {
            this.response = response;
            this.userName = userName;
            this.date = date;
        }
    }

}
