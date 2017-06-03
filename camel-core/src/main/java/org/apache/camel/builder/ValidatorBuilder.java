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
package org.apache.camel.builder;

import org.apache.camel.CamelContext;
import org.apache.camel.Expression;
import org.apache.camel.Predicate;
import org.apache.camel.impl.validator.ProcessorValidator;
import org.apache.camel.model.language.ExpressionDefinition;
import org.apache.camel.model.validator.CustomValidatorDefinition;
import org.apache.camel.model.validator.EndpointValidatorDefinition;
import org.apache.camel.model.validator.PredicateValidatorDefinition;
import org.apache.camel.model.validator.ValidatorDefinition;
import org.apache.camel.spi.AsPredicate;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.Validator;

/**
 * A <a href="http://camel.apache.org/dsl.html">Java DSL</a> which is
 * used to build a {@link org.apache.camel.spi.Validator} and register into {@link org.apache.camel.CamelContext}.
 * It requires a 'type' to be specified by type() method.
 * And then you can choose a type of validator by withUri(), withPredicate(), withJava() or withBean() method.
 */
public class ValidatorBuilder {

    private String type;
    private String uri;
    private ExpressionDefinition expression;
    private Class<? extends Validator> clazz;
    private String beanRef;

    /**
     * Set the data type name.
     * If you specify 'xml:XYZ', the validator will be picked up if source type is
     * 'xml:XYZ'. If you specify just 'xml', the validator matches with all of
     * 'xml' source type like 'xml:ABC' or 'xml:DEF'.
     *
     * @param type 'from' data type name
     */
    public ValidatorBuilder type(String type) {
        this.type = type;
        return this;
    }

    /**
     * Set the data type using Java class.
     *
     * @param type Java class represents data type
     */
    public ValidatorBuilder type(Class<?> type) {
        this.type = new DataType(type).toString();
        return this;
    }

    /**
     * Set the URI to be used for the endpoint {@link Validator}.
     * @see {@link EndpointValidatorDefinition}, {@link ProcessorValidator}
     * 
     * @param uri endpoint URI
     */
    public ValidatorBuilder withUri(String uri) {
        resetType();
        this.uri = uri;
        return this;
    }

    /**
     * Set the {@link Expression} to be used for the predicate {@link Validator}.
     * @see {@link PredicateValidatorDefinition}, {@link ProcessorValidator}
     * 
     * @param expression validation expression
     */
    public ValidatorBuilder withExpression(@AsPredicate Expression expression) {
        resetType();
        this.expression = new ExpressionDefinition(expression);
        return this;
    }

    /**
     * Set the {@link Predicate} to be used for the predicate {@link Validator}.
     * @see {@link PredicateValidatorDefinition}, {@link ProcessorValidator}
     * 
     * @param predicate validation predicate
     */
    public ValidatorBuilder withExpression(@AsPredicate Predicate predicate) {
        resetType();
        this.expression = new ExpressionDefinition(predicate);
        return this;
    }

    /**
     * Set the Java {@code Class} represents a custom {@code Validator} implementation class.
     * @see {@code CustomValidatorDefinition}
     * 
     * @param clazz {@code Class} object represents custom validator implementation
     */
    public ValidatorBuilder withJava(Class<? extends Validator> clazz) {
        resetType();
        this.clazz = clazz;
        return this;
    }

    /**
     * Set the Java Bean name to be used for custom {@code Validator}.
     * @see {@code CustomValidatorDefinition}
     * 
     * @param ref bean name for the custom {@code Validator}
     */
    public ValidatorBuilder withBean(String ref) {
        resetType();
        this.beanRef = ref;
        return this;
    }

    private void resetType() {
        this.uri = null;
        this.expression = null;
        this.clazz = null;
        this.beanRef = null;
    }

    /**
     * Configure a Validator according to the configurations built on this builder
     * and register it into given {@code CamelContext}.
     * 
     * @param camelContext {@code CamelContext}
     */
    public void configure(CamelContext camelContext) {
        ValidatorDefinition validator;
        if (uri != null) {
            EndpointValidatorDefinition etd = new EndpointValidatorDefinition();
            etd.setUri(uri);
            validator = etd;
        } else if (expression != null) {
            PredicateValidatorDefinition dtd = new PredicateValidatorDefinition();
            dtd.setExpression(expression);
            validator = dtd;
        } else if (clazz != null) {
            CustomValidatorDefinition ctd = new CustomValidatorDefinition();
            ctd.setClassName(clazz.getName());
            validator = ctd;
        } else if (beanRef != null) {
            CustomValidatorDefinition ctd = new CustomValidatorDefinition();
            ctd.setRef(beanRef);
            validator = ctd;
        } else {
            throw new IllegalArgumentException("No Validator type was specified");
        }
        
        validator.setType(type);
        camelContext.getValidators().add(validator);
    }
}
