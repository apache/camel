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
package org.apache.camel.test.cdi;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to be used to customise the deployment configured by the {@code CamelCdiRunner}.
 *
 * @see CamelCdiRunner
 */
@Documented
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Beans {

    /**
     * Returns the list of <a href="http://docs.jboss.org/cdi/spec/1.2/cdi-spec.html#alternatives">alternatives</a>
     * to be selected in the application.
     * <p/>
     * Note that the declared alternatives are globally selected for the entire
     * application. For example, if you have the following named bean in your
     * application:
     * <pre><code>
     * {@literal @}Named("foo")
     * public class FooBean {
     *
     * }
     * </code></pre>
     *
     * It can be replaced in your test by declaring the following alternative
     * bean:
     * <pre><code>
     * {@literal @}Alternative
     * {@literal @}Named("foo")
     * public class AlternativeBean {
     *
     * }
     * </code></pre>
     *
     * And adding the {@code @Beans} annotation to you test class to activate it:
     * <pre><code>
     * {@literal @}RunWith(CamelCdiRunner.class)
     * {@literal @}Beans(alternatives = AlternativeBean.class)
     * public class TestWithAlternative {
     *
     * }
     * </code></pre>
     *
     * @see javax.enterprise.inject.Alternative
     */
    Class<?>[] alternatives() default {};

    /**
     * Returns the list of classes to be added as beans in the application.
     *
     * That can be used to add classes to the deployment for test purpose
     * in addition to the test class which is automatically added as bean.
     *
     */
    Class<?>[] classes() default {};

    /**
     * Returns the list of classes whose packages are to be added for beans
     * discovery.
     *
     * That can be used to add packages to the deployment for test purpose
     * in addition to the test class which is automatically added as bean.
     */
    Class<?>[] packages() default {};
}
