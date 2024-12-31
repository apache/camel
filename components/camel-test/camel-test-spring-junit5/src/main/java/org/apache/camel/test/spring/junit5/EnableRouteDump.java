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
package org.apache.camel.test.spring.junit5;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Whether to dump the routes loaded into Camel for each test (dumped into files in target/camel-route-dump).
 * <p/>
 * The routes can either be dumped into XML or YAML format.
 * <p/>
 * This allows tooling or manual inspection of the routes.
 * <p/>
 * You can also turn on route dump globally via setting JVM system property <tt>CamelTestRouteDump=xml</tt>.
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.TYPE })
public @interface EnableRouteDump {

    /**
     * The format to dump as either xml or yaml. You can use false to turn of route dump. Uses xml as default.
     */
    String format() default "xml";

}
