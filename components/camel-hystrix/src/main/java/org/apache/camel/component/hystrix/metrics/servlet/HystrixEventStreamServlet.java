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
package org.apache.camel.component.hystrix.metrics.servlet;

/**
 * Streams Hystrix metrics in text/event-stream format.
 * <p/>
 * Install by:
 * <p/>
 * 1) Including camel-hystrix-*.jar in your classpath.
 * <p/>
 * 2) Adding the following to web.xml:
 * <pre>{@code
 * <servlet>
 *  <display-name>HystrixEventStreamServlet</display-name>
 *  <servlet-name>HystrixEventStreamServlet</servlet-name>
 *  <servlet-class>org.apache.camel.component.hystrix.metrics.servlet.HystrixEventStreamServlet</servlet-class>
 * </servlet>
 * <servlet-mapping>
 *  <servlet-name>HystrixEventStreamServlet</servlet-name>
 *  <url-pattern>/hystrix.stream</url-pattern>
 * </servlet-mapping>
 * } </pre>
 * <p/>
 * See more details at: https://github.com/Netflix/Hystrix/tree/master/hystrix-contrib/hystrix-metrics-event-stream
 */
public class HystrixEventStreamServlet extends com.netflix.hystrix.contrib.metrics.eventstream.HystrixMetricsStreamServlet {
}
