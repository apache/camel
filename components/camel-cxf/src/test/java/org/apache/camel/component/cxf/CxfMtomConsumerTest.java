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
package org.apache.camel.component.cxf;

import java.awt.Image;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.util.List;

import javax.imageio.ImageIO;
import javax.xml.namespace.QName;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.Holder;
import javax.xml.ws.soap.SOAPBinding;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.cxf.mtom_feature.Hello;
import org.apache.camel.cxf.mtom_feature.HelloService;
import org.apache.cxf.BusFactory;


public class CxfMtomConsumerTest extends ContextTestSupport {
    protected static final String MTOM_ENDPOINT_ADDRESS = "http://localhost:9090/jaxws-mtom/hello";
    protected static final String MTOM_ENDPOINT_URI = "cxf://" + MTOM_ENDPOINT_ADDRESS
        + "?serviceClass=org.apache.camel.component.cxf.HelloImpl";        

    private final QName serviceName = new QName("http://apache.org/camel/cxf/mtom_feature", "HelloService");
    

    protected RouteBuilder createRouteBuilder() {      
        
        return new RouteBuilder() {
            public void configure() {
                from(MTOM_ENDPOINT_URI).process(new Processor() {
                    @SuppressWarnings("unchecked")
                    public void process(final Exchange exchange) throws Exception {
                        Message in = exchange.getIn();
                        // Get the parameter list
                        List<?> parameter = in.getBody(List.class);
                        // Get the operation name
                        Holder<byte[]> photo = (Holder<byte[]>)parameter.get(0);
                        assertNotNull("The photo should not be null", photo.value);
                        assertEquals("Should get the right request", new String(photo.value, "UTF-8"),
                                     "RequestFromCXF");
                        photo.value = "ResponseFromCamel".getBytes("UTF-8");
                        Holder<Image> image = (Holder<Image>)parameter.get(1);
                        assertNotNull("We should get the image here", image.value);
                        // set the holder message back    
                        exchange.getOut().setBody(new Object[] {null, photo, image});

                    }
                });                
            }
        };
    }
    
    private Hello getPort() {
        URL wsdl = getClass().getResource("/mtom.wsdl");
        assertNotNull("WSDL is null", wsdl);

        HelloService service = new HelloService(wsdl, serviceName);
        assertNotNull("Service is null ", service);
        return service.getHelloPort();
    }
    
    private Image getImage(String name) throws Exception {
        return ImageIO.read(getClass().getResource(name));
    }
    
    public void testInvokingServiceFromCXFClient() throws Exception {        

        if (Boolean.getBoolean("java.awt.headless")) {
            System.out.println("Running headless. Skipping test as Images may not work.");
            return;
        }        
        
        Holder<byte[]> photo = new Holder<byte[]>("RequestFromCXF".getBytes("UTF-8"));
        Holder<Image> image = new Holder<Image>(getImage("/java.jpg"));

        Hello port = getPort();

        SOAPBinding binding = (SOAPBinding) ((BindingProvider)port).getBinding();
        binding.setMTOMEnabled(true);

        port.detail(photo, image);

        assertEquals("ResponseFromCamel", new String(photo.value, "UTF-8"));
        assertNotNull(image.value);        
        
    }

}
