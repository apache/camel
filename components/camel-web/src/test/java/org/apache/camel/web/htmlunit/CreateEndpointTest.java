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
package org.apache.camel.web.htmlunit;

import java.util.List;

import org.apache.camel.web.htmlunit.pages.EndpointsPage;
import org.apache.camel.web.htmlunit.pages.SendMessagePage;
import org.openqa.selenium.WebElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;
import static org.openqa.selenium.By.xpath;
import static org.openqa.selenium.lift.Finders.links;
import static org.openqa.selenium.lift.Matchers.atLeast;
import static org.openqa.selenium.lift.Matchers.text;

/**
 * @version 
 */
public class CreateEndpointTest extends HtmlTestSupport {
    private static final Logger LOG = LoggerFactory.getLogger(CreateEndpointTest.class);

    protected String newEndpointUri = "mock:myNewlyCreatedEndpoint";
    protected String messageBody = "hello world!";

    public void testCreateEndpoint() throws Exception {

        goToRootPage();

        assertPresenceOf(atLeast(4), links().with(text(not(equalTo("Images")))));

        // TODO one day we should do this
        //clickOn(xpath("//a[text() = 'Endpoints']"));
        WebElement element = findElement(xpath("//a[text() = 'Endpoints']"));
        element.click();

        assertThat(getTitle(), containsString("Endpoints"));

        List<WebElement> endpointLinks = findElements(xpath("//a[@class='endpoint']"));
        assertTrue("Should be several endpoint links!", endpointLinks.size() > 0);

        LOG.info("Found " + endpointLinks.size() + " endpoints links!");


        // lets create a new endpoint
        new EndpointsPage(getWebDriver()).createEndpoint(newEndpointUri);

        assertThat(getTitle(), containsString(newEndpointUri));


        // now lets send a message to it
        findElement(xpath("//a[@class='send']")).click();

        new SendMessagePage(getWebDriver()).sendMessage(messageBody);

        // now lets view the message
        // TODO can we force the last link to be clicked via xpath?
        findElement(xpath("//a[@class='message']")).click();

        String actualMessage = findElement(xpath("//div[@class='message']")).getText();
        LOG.info("Found message body: " + actualMessage);

        assertThat(actualMessage, containsString(messageBody));

        LOG.debug("Source: " + getPageSource());
    }

}
