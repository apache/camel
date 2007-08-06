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
package org.apache.camel.component.validator;

import org.apache.camel.RuntimeCamelException;
import org.apache.camel.processor.validation.SchemaValidationException;
import org.apache.camel.spring.SpringTestSupport;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * @version $Revision: $
 */
public class ValidatorRouteTest extends SpringTestSupport {

    public void testValidMessage() throws Exception {
        String body = "<mail xmlns='http://foo.com/bar'><subject>Hey</subject><body>Hello world!</body></mail>";
        try {
            template.sendBody("direct:start", body);
        }
        catch (Throwable e) {
            log.error(e, e);
            fail("Caught: " + e);
        }
    }

    public void testInvalidMessage() throws Exception {
        String body = "<mail xmlns='http://foo.com/bar'><body>Hello world!</body></mail>";
        try {
            template.sendBody("direct:start", body);
        }

        // TODO ideally we'd not have to wrap validation exceptions!
        // TODO should we expose checked exceptions on CamelTemplate
        // or should we make validation errors be runtime exceptions?
        catch (RuntimeCamelException e) {
            Throwable cause = e.getCause();
            if (cause instanceof SchemaValidationException) {
                log.debug("Caught expected schema validation exception: " + e, e);
            }
            else {
                log.error(e, e);
                fail("Not a SchemaValidationException: " + e);
            }
        }
        catch (Throwable e) {
            log.error(e, e);
            fail("Caught: " + e);
        }
    }

    protected int getExpectedRouteCount() {
        // TODO why zero?
        return 0;
    }

    protected ClassPathXmlApplicationContext createApplicationContext() {
        return new ClassPathXmlApplicationContext("org/apache/camel/component/validator/camelContext.xml");
    }

}