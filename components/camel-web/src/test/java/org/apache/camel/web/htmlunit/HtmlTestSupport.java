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

import com.gargoylesoftware.htmlunit.WebClient;

import org.apache.camel.web.Main;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.htmlunit.HtmlUnitDriver;
import org.openqa.selenium.lift.HamcrestWebDriverTestCase;

/**
 * @version 
 */
public abstract class HtmlTestSupport extends HamcrestWebDriverTestCase {
    protected WebDriver webDriver;

    /**
     * Go to the home page of the web application
     */
    protected void goToRootPage() {
        goTo(Main.getRootUrl());
    }
    
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        Main.start();
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        Main.stop();
    }

    @Override
    protected WebDriver createDriver() {
        if (webDriver == null) {
            webDriver = new HtmlUnitDriver() {
                @Override
                protected WebClient modifyWebClient(WebClient webClient) {
                    WebClient answer = super.modifyWebClient(webClient);
                    answer.addRequestHeader("Accept", "text/html");
                    answer.getCookieManager().setCookiesEnabled(true);
                    return answer;
                }
            };
        }
        return webDriver;
    }

    // TODO remove these methods if/when they are available in the base class!!!
    //-------------------------------------------------------------------------
    protected WebDriver getWebDriver() {
        return webDriver;
    }

    public WebElement findElement(By by) {
        return getWebDriver().findElement(by);
    }

    public List<WebElement> findElements(By by) {
        return getWebDriver().findElements(by);
    }

    public void get(String s) {
        getWebDriver().get(s);
    }

    public String getCurrentUrl() {
        return getWebDriver().getCurrentUrl();
    }

    public String getPageSource() {
        return getWebDriver().getPageSource();
    }

    public String getTitle() {
        return getWebDriver().getTitle();
    }

    public String getWindowHandle() {
        return getWebDriver().getWindowHandle();
    }

    public WebDriver.TargetLocator switchTo() {
        return getWebDriver().switchTo();
    }

}
