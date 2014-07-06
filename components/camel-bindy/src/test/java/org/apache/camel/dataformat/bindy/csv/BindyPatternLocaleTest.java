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
package org.apache.camel.dataformat.bindy.csv;

import java.util.Arrays;
import java.util.Collection;
import java.util.Locale;

import org.apache.camel.builder.AdviceWithRouteBuilder;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.dataformat.bindy.model.padding.Unity;
import org.apache.camel.test.junit4.CamelTestSupport;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * @version 
 */
@RunWith(Parameterized.class)
public class BindyPatternLocaleTest extends CamelTestSupport {
	
	private String locale;
	
	public BindyPatternLocaleTest(String locale) {
			this.locale = locale;
   }

	@Parameters
	public static Collection<String[]> localeList() {
		return Arrays.asList(new String[][] { 
				{Locale.CANADA.getCountry()}, 
				{Locale.CANADA_FRENCH.getCountry()},
				{Locale.CHINA.getCountry()},
				{Locale.CHINESE.getLanguage()},
				{Locale.ENGLISH.getLanguage()},
				{Locale.FRANCE.getCountry()},
				{Locale.FRENCH.getLanguage()},
				{Locale.GERMAN.getLanguage()},
				{Locale.GERMANY.getCountry()},
				{Locale.ITALIAN.getLanguage()},
				{Locale.ITALY.getCountry()},
				{Locale.JAPAN.getCountry()},
				{Locale.JAPANESE.getLanguage()},
				{Locale.KOREA.getCountry()},
				{Locale.KOREAN.getLanguage()},
				{Locale.PRC.getCountry()},
				{Locale.SIMPLIFIED_CHINESE.getLanguage()},
				{Locale.TAIWAN.getCountry()},
				{Locale.TRADITIONAL_CHINESE.getLanguage()},
				{Locale.UK.getCountry()},
				{Locale.US.getCountry()},
		});
	}

	@Test
	public void testMarshalling() throws Exception {
		context.getRouteDefinitions().get(0)
				.adviceWith(context, new AdviceWithRouteBuilder() {
					@Override
					public void configure() throws Exception {
						BindyCsvDataFormat bindy = new BindyCsvDataFormat(
								Unity.class);

						// As recommended, when we use @Datafield Pattern we
						// must specify the default locale
						bindy.setLocale(locale);

						// weave the node in the route which has id = marshaller
						// and replace it with the following route path
						weaveById("marshaller").replace().marshal(bindy);
					}
				});
		MockEndpoint mock = getMockEndpoint("mock:marshal");
		mock.expectedMessageCount(1);
		mock.expectedBodiesReceived("050,010\r\n");

		Unity unity = new Unity();
		unity.setMandant(50f);
		unity.setReceiver(10f);
		template.sendBody("direct:marshal", unity);

		assertMockEndpointsSatisfied();
	}

	@Override
	protected RouteBuilder createRouteBuilder() throws Exception {
		return new RouteBuilder() {
			@Override
			public void configure() throws Exception {
				BindyCsvDataFormat bindy = new BindyCsvDataFormat(Unity.class);

				// As recommended, when we use @Datafield Pattern we must
				// specify the default locale
				bindy.setLocale("default");

				from("direct:marshal").marshal(bindy).id("marshaller")
						.to("mock:marshal");
			}
		};
	}
}
