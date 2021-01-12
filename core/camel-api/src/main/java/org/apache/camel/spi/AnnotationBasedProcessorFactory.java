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
package org.apache.camel.spi;

import org.apache.camel.AsyncProcessor;
import org.apache.camel.CamelContext;
import org.apache.camel.DynamicRouter;
import org.apache.camel.Processor;
import org.apache.camel.RecipientList;
import org.apache.camel.RoutingSlip;

/**
 * Factory to create {@link Processor} for annotation based EIPs.
 */
public interface AnnotationBasedProcessorFactory {

    /**
     * Service factory key.
     */
    String FACTORY = "annotation-processor-factory";

    /**
     * Creates dynamic router processor from the configured annotation.
     */
    AsyncProcessor createDynamicRouter(CamelContext camelContext, DynamicRouter annotation);

    /**
     * Creates recipient list processor from the configured annotation.
     */
    AsyncProcessor createRecipientList(CamelContext camelContext, RecipientList annotation);

    /**
     * Creates routing slip processor from the configured annotation.
     */
    AsyncProcessor createRoutingSlip(CamelContext camelContext, RoutingSlip annotation);

}
