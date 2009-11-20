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
package org.apache.camel.example.gae;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.gae.mail.GMailBinding;

public class TutorialRouteBuilder extends RouteBuilder {

    private String sender;
    
    public void setSender(String sender) {
        this.sender = sender;
    }
    
    @Override
    public void configure() throws Exception {
        from("ghttp:///weather")
            .to("gtask://default")
            .setHeader(Exchange.CONTENT_TYPE, constant("text/plain"))
            .transform(constant("Weather report will be sent to ").append(header("mailto")));
      
        from("gtask://default")
            .setHeader(Exchange.HTTP_QUERY, constant("weather=").append(header("city")))
            .to("ghttp://www.google.com/ig/api")
            .process(new WeatherProcessor())        
            .setHeader(GMailBinding.GMAIL_SUBJECT, constant("Weather report"))
            .setHeader(GMailBinding.GMAIL_TO, header("mailto"))
            .to("gmail://" + sender);
    }

}
