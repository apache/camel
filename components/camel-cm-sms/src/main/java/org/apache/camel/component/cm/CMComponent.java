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
package org.apache.camel.component.cm;

import java.util.Map;
import java.util.Set;

import javax.validation.ConstraintViolation;
import javax.validation.Validation;
import javax.validation.Validator;
import javax.validation.ValidatorFactory;

import org.apache.camel.CamelContext;
import org.apache.camel.Endpoint;
import org.apache.camel.ResolveEndpointFailedException;
import org.apache.camel.spi.annotations.Component;
import org.apache.camel.support.DefaultComponent;

/**
 * Represents the component that manages {@link CMEndpoint}s.
 */
@Component("cm-sms")
public class CMComponent extends DefaultComponent {

    private Validator validator;

    public CMComponent() {
    }

    public CMComponent(final CamelContext context) {
        super(context);
    }

    @Override
    protected Endpoint createEndpoint(final String uri, final String remaining, final Map<String, Object> parameters) throws Exception {
        CMEndpoint endpoint = new CMEndpoint(uri, this);
        endpoint.setHost(remaining);
        setProperties(endpoint, parameters);

        // Validate configuration
        final Set<ConstraintViolation<CMConfiguration>> constraintViolations = getValidator().validate(endpoint.getConfiguration());
        if (constraintViolations.size() > 0) {
            final StringBuffer msg = new StringBuffer();
            for (final ConstraintViolation<CMConfiguration> cv : constraintViolations) {
                msg.append(String.format("- Invalid value for %s: %s",
                        cv.getPropertyPath().toString(),
                        cv.getMessage()));
            }
            throw new ResolveEndpointFailedException(uri, msg.toString());
        }

        return endpoint;
    }

    public Validator getValidator() {
        if (validator == null) {
            ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
            validator = factory.getValidator();
        }
        return validator;
    }

    @Override
    protected void doInit() throws Exception {
        super.doInit();
    }
}
