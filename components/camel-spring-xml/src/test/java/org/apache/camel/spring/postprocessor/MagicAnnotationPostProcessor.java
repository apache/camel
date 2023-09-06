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
package org.apache.camel.spring.postprocessor;

import java.lang.reflect.Field;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.SmartInstantiationAwareBeanPostProcessor;
import org.springframework.stereotype.Component;
import org.springframework.util.ReflectionUtils;

/**
 * Trivial post processor which sets the value of the annotation to the field it is applied to
 */
@Component
public class MagicAnnotationPostProcessor implements SmartInstantiationAwareBeanPostProcessor {

    protected Logger log = LoggerFactory.getLogger(getClass());

    @Override
    public boolean postProcessAfterInstantiation(final Object bean, final String beanName) throws BeansException {
        ReflectionUtils.doWithFields(bean.getClass(), new ReflectionUtils.FieldCallback() {
            public void doWith(Field field) throws IllegalArgumentException, IllegalAccessException {
                MagicAnnotation annotation = field.getAnnotation(MagicAnnotation.class);
                if (annotation != null && field.getType() == String.class) {
                    log.info("Found MagicAnnotation on field {} of class {}", field, bean.getClass());

                    ReflectionUtils.makeAccessible(field);
                    field.set(bean, annotation.value());
                }
            }
        });

        return true;
    }

}
