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
package org.apache.camel.dataformat.any23;

import java.util.List;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;

public class Any23DataFormatBasicTest extends CamelTestSupport {

  @Test
  public void testUnMarshalToStringOfXml() throws Exception {
    MockEndpoint resultEndpoint = resolveMandatoryEndpoint("mock:result", MockEndpoint.class);
    //resultEndpoint.expectedMessageCount(2);

    //  String badHtml = TidyMarkupTestSupport.loadFileAsString(new File(
    //          "src/test/resources/org/apache/camel/dataformat/any23/testfile1.html"));
    //   String evilHtml = TidyMarkupTestSupport.loadFileAsString(new File(
    //          "src/test/resources/org/apache/camel/dataformat/any23/testfile2-evilHtml.html"));
    String contenhtml = "<div id='hcard-JOSE-LUIS-SEGARRA-FLORES' class='vcard'> "
        + " <a class='url fn n' href='https://www.youtube.com/watch?v=kg1BljLu9YY'>  <span class='given-name'>JOSE</span> "
        + "  <span class='additional-name'>LUIS</span> "
        + "  <span class='family-name'>SEGARRA FLORES</span> "
        + "</a> "
        + " <div class='org'>TransExpress</div> "
        + " <a class='email' href='mailto:joesega7@gmail.com'>joesega7@gmail.com</a> "
        + " <div class='adr'> "
        + "  <div class='street-address'>7801 NW 37th Street Doral,  FL 33195-6503</div> "
        + "  <span class='locality'>Doral</span> "
        + ",  "
        + "  <span class='region'>Florida</span> "
        + ",  "
        + "  <span class='postal-code'>33195-6503</span> "
        + " "
        + "  <span class='country-name'>Estados Unidos</span> "
        + " "
        + " </div> "
        + " <div class='tel'>3055920839</div> "
        + "<p style='font-size:smaller;'>This <a href='http://microformats.org/wiki/hcard'>hCard</a> created with the <a href='http://microformats.org/code/hcard/creator'>hCard creator</a>.</p> "
        + "</div>";

    final String content = "<span class='vcard'> "
        + "  <span class='fn'>L'Amourita Pizza</span> "
        + "   Located at "
        + "  <span class='adr'> "
        + "    <span class='street-address'>123 Main St</span>, "
        + "    <span class='locality'>Albequerque</span>, "
        + "    <span class='region'>NM</span>. "
        + "  </span> "
        + "  <a href='http://pizza.example.com' class='url'>http://pizza.example.com</a> "
        + "</span>   ";

    template.sendBody("direct:start", contenhtml);
    //  template.sendBody("direct:start", evilHtml);

    //resultEndpoint.assertIsSatisfied(); 
    List<Exchange> list = resultEndpoint.getReceivedExchanges();
    System.out.print(list.size());
    for (Exchange exchange : list) {

      Message in = exchange.getIn();
      //  Node tidyMarkup = in.getBody(Node.class);
      System.out.print(in.getBody(String.class));
      log.info("Received " + in.getBody(String.class));
      // assertNotNull("Should be able to convert received body to a string", tidyMarkup);

    }
  }

  @Override
  protected RouteBuilder createRouteBuilder() {
    return new RouteBuilder() {
      public void configure() {
        from("direct:start").marshal().any23("http://pizza.example.com").to("mock:result");
        //from("direct:start").unmarshal().any23().to("mock:result");
        //  from("direct:start").marshal().tidyMarkup();
        //  from("direct:start").unmarshal().tidyMarkup().to("mock:result");
      }
    };
  }

}
