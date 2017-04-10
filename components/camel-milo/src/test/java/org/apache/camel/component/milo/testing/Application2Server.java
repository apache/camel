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
package org.apache.camel.component.milo.testing;

import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.milo.server.MiloServerComponent;
import org.apache.camel.impl.DefaultCamelContext;

public class Application2Server {
	public static void main(final String[] args) throws Exception {

		// camel conext

		final CamelContext context = new DefaultCamelContext();

		// add paho

		// no need to register, gets auto detected
		// context.addComponent("paho", new PahoComponent());
		((MiloServerComponent) context.getComponent("milo-server")).setUserAuthenticationCredentials("foo:bar");

		// add routes

		context.addRoutes(new RouteBuilder() {

			@Override
			public void configure() throws Exception {
				/*
				 * from(
				 * "paho:javaonedemo/eclipse-greenhouse-9home/sensors/temperature?brokerUrl=tcp://iot.eclipse.org:1883")
				 * .log("Temp update: ${body}").convertBodyTo(String.class).to(
				 * "milo-server:MyItem");
				 */

				from("paho:my/foo/bar?brokerUrl=tcp://iot.eclipse.org:1883").log("Temp update: ${body}")
						.convertBodyTo(String.class).to("milo-server:MyItem");

				from("milo-server:MyItem").log("MyItem: ${body}");
				from("milo-server:MyItem2").log("MyItem2: ${body}").convertBodyTo(String.class)
						.to("paho:de/dentrassi/camel/milo/temperature?brokerUrl=tcp://iot.eclipse.org:1883");

				from("paho:de/dentrassi/camel/milo/temperature?brokerUrl=tcp://iot.eclipse.org:1883")
						.log("Back from MQTT: ${body}");
			}
		});

		// start

		context.start();

		// sleep

		while (true) {
			Thread.sleep(Long.MAX_VALUE);
		}
	}
}
