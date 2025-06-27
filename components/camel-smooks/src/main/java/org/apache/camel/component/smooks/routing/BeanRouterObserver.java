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
package org.apache.camel.component.smooks.routing;

import org.smooks.api.bean.context.BeanContext;
import org.smooks.api.bean.lifecycle.BeanContextLifecycleEvent;
import org.smooks.api.bean.lifecycle.BeanContextLifecycleObserver;
import org.smooks.api.bean.lifecycle.BeanLifecycle;
import org.smooks.api.expression.ExecutionContextExpressionEvaluator;
import org.smooks.assertion.AssertArgument;

/**
 * BeanRouterObserver is a {@link BeanContextLifecycleObserver} that will route a specified bean to the configured
 * endpoint.
 * </p>
 *
 */
public class BeanRouterObserver implements BeanContextLifecycleObserver {
    private final BeanRouter beanRouter;
    private final String beanId;
    private ExecutionContextExpressionEvaluator conditionEvaluator;

    /**
     * Sole contructor.
     *
     * @param beanRouter The bean router instance to be used for routing beans.
     * @param beanId     The beanId which is the beanId in the Smooks {@link BeanContext}.
     */
    public BeanRouterObserver(final BeanRouter beanRouter, final String beanId) {
        AssertArgument.isNotNull(beanRouter, "beanRouter");
        AssertArgument.isNotNull(beanId, "beanId");

        this.beanRouter = beanRouter;
        this.beanId = beanId;
    }

    /**
     * Set the condition evaluator for performing the routing.
     * <p/>
     * Used to test if the routing is to be performed based on the user configured condition.
     *
     * @param conditionEvaluator The routing condition evaluator.
     */
    public void setConditionEvaluator(ExecutionContextExpressionEvaluator conditionEvaluator) {
        this.conditionEvaluator = conditionEvaluator;
    }

    /**
     * Will route to the endpoint if the BeanLifecycle is of type BeanLifecycle.REMOVE and the beanId is equals to the
     * beanId that was configured for this instance.
     */
    public void onBeanLifecycleEvent(final BeanContextLifecycleEvent event) {
        if (endEventAndBeanIdMatch(event) && conditionsMatch(event)) {
            beanRouter.sendBean(event.getBean(), event.getExecutionContext());
        }
    }

    private boolean endEventAndBeanIdMatch(final BeanContextLifecycleEvent event) {
        return event.getLifecycle() == BeanLifecycle.END_FRAGMENT && event.getBeanId().getName().equals(beanId);
    }

    public boolean conditionsMatch(BeanContextLifecycleEvent event) {
        if (conditionEvaluator == null) {
            return true;
        }

        try {
            return conditionEvaluator.eval(event.getExecutionContext());
        } catch (Exception e) {
            return false;
        }
    }
}
