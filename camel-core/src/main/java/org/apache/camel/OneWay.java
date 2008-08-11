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
package org.apache.camel;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method as being a one way asynchronous invocation so that if you are using some kind of
 * <a href="http://activemq.apache.org/camel/spring-remoting.html">Spring Remoting</a> then the method invocation will be asynchronous.
 *
 * If you wish to use some other {@link ExchangePattern} than {@link org.apache.camel.ExchangePattern#InOnly} you could use something like
 *
 * <code>
 * @OneWay(ExchangePattern.RobustInOnly)
 * public void myMethod() {...}
 * </code>
 *
 * otherwise the following code would default to using {@link org.apache.camel.ExchangePattern#InOnly}
 *
 * <code>
 * @OneWay
 * public void myMethod() {...}
 * </code>
 *
 * @see ExchangePattern
 * @see Exchange#getPattern()
 *
 * @version $Revision$
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD })
public @interface OneWay {

    /**
     * Allows the exact exchange pattern type to be specified though the default value of
     * {@link org.apache.camel.ExchangePattern#InOnly} should be fine for most uses
     */
    public abstract ExchangePattern value() default ExchangePattern.InOnly;
}