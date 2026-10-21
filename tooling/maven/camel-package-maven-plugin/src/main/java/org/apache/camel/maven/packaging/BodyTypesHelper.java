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
package org.apache.camel.maven.packaging;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.camel.spi.BodyType;
import org.apache.camel.spi.BodyType.Carrier;
import org.apache.camel.spi.BodyTypes;
import org.apache.camel.tooling.model.BaseModel;
import org.apache.camel.tooling.model.BaseOptionModel;
import org.apache.camel.tooling.model.BodyTypeModel;
import org.apache.camel.tooling.model.ComponentModel;

/**
 * Carries the {@link BodyTypes} of a component, data format or language into its model, which the catalog JSON is
 * written from (CAMEL-24961).
 */
final class BodyTypesHelper {

    private static final Set<String> IN = Set.of(BodyTypeModel.CONSUMER_REPLY, BodyTypeModel.PRODUCER_IN,
            BodyTypeModel.MARSHAL_IN, BodyTypeModel.UNMARSHAL_IN, BodyTypeModel.EXPRESSION_IN);

    private BodyTypesHelper() {
    }

    /**
     * Adds the sides the annotation gives to the model, in the order of the annotation.
     *
     * @param  model                    the model of the component, data format or language
     * @param  bodyTypes                the annotation, null when the class has none
     * @param  owner                    the class with the annotation, for the error message
     * @throws IllegalArgumentException when a side does not fit the model: a data format side on a component, a
     *                                  producer side on a consumer-only component, unchanged on what a step needs, any
     *                                  on what it leaves, or a value an option cannot have
     */
    static void addBodyTypes(BaseModel<?> model, BodyTypes bodyTypes, Class<?> owner) {
        if (bodyTypes == null) {
            return;
        }
        Map<String, BodyType> sides = new LinkedHashMap<>();
        sides.put(BodyTypeModel.CONSUMER_OUT, bodyTypes.consumerOut());
        sides.put(BodyTypeModel.CONSUMER_REPLY, bodyTypes.consumerReply());
        sides.put(BodyTypeModel.PRODUCER_IN, bodyTypes.producerIn());
        sides.put(BodyTypeModel.PRODUCER_OUT, bodyTypes.producerOut());
        sides.put(BodyTypeModel.MARSHAL_IN, bodyTypes.marshalIn());
        sides.put(BodyTypeModel.MARSHAL_OUT, bodyTypes.marshalOut());
        sides.put(BodyTypeModel.UNMARSHAL_IN, bodyTypes.unmarshalIn());
        sides.put(BodyTypeModel.UNMARSHAL_OUT, bodyTypes.unmarshalOut());
        sides.put(BodyTypeModel.EXPRESSION_IN, bodyTypes.expressionIn());
        sides.put(BodyTypeModel.EXPRESSION_OUT, bodyTypes.expressionOut());

        Set<String> allowed = allowedSides(model);
        Map<String, BaseOptionModel> options = options(model);
        for (Map.Entry<String, BodyType> entry : sides.entrySet()) {
            String side = entry.getKey();
            BodyType bodyType = entry.getValue();
            if (bodyType.value().length == 0) {
                if (!bodyType.javaType().isEmpty() || !bodyType.format().isEmpty() || bodyType.dependsOn().length > 0
                        || bodyType.byOption().length > 0 || !bodyType.description().isEmpty()) {
                    throw new IllegalArgumentException(
                            owner.getName() + ": the @BodyTypes " + side + " has no carriers, but other attributes");
                }
                continue;
            }
            if (!allowed.contains(side)) {
                throw new IllegalArgumentException(
                        owner.getName() + ": the @BodyTypes " + side + " is not a side of this " + model.getKind()
                                                   + ", which has " + allowed);
            }
            check(owner, side, bodyType.value());
            for (String option : bodyType.dependsOn()) {
                checkOption(owner, side, option, options);
            }
            for (BodyType.When when : bodyType.byOption()) {
                checkOption(owner, side, when.option(), options);
                checkValue(owner, side, when, options.get(when.option()));
                check(owner, side + " " + when.option() + "=" + when.value(), when.carriers());
            }
            model.getBodyTypes().put(side, asModel(bodyType));
        }
    }

    private static Set<String> allowedSides(BaseModel<?> model) {
        return switch (model.getKind()) {
            case component -> {
                ComponentModel component = (ComponentModel) model;
                if (component.isConsumerOnly()) {
                    yield Set.of(BodyTypeModel.CONSUMER_OUT, BodyTypeModel.CONSUMER_REPLY);
                } else if (component.isProducerOnly()) {
                    yield Set.of(BodyTypeModel.PRODUCER_IN, BodyTypeModel.PRODUCER_OUT);
                }
                yield Set.of(BodyTypeModel.CONSUMER_OUT, BodyTypeModel.CONSUMER_REPLY, BodyTypeModel.PRODUCER_IN,
                        BodyTypeModel.PRODUCER_OUT);
            }
            case dataformat -> Set.of(BodyTypeModel.MARSHAL_IN, BodyTypeModel.MARSHAL_OUT, BodyTypeModel.UNMARSHAL_IN,
                    BodyTypeModel.UNMARSHAL_OUT);
            case language -> Set.of(BodyTypeModel.EXPRESSION_IN, BodyTypeModel.EXPRESSION_OUT);
            default -> Set.of();
        };
    }

    /** The options of the model by name, which the body types can depend on. */
    private static Map<String, BaseOptionModel> options(BaseModel<?> model) {
        Map<String, BaseOptionModel> answer = new HashMap<>();
        model.getOptions().forEach(o -> answer.put(o.getName(), o));
        if (model instanceof ComponentModel component) {
            component.getComponentOptions().forEach(o -> answer.put(o.getName(), o));
            component.getEndpointOptions().forEach(o -> answer.put(o.getName(), o));
        }
        return answer;
    }

    private static void checkOption(Class<?> owner, String side, String option, Map<String, BaseOptionModel> options) {
        if (!options.containsKey(option)) {
            throw new IllegalArgumentException(
                    owner.getName() + ": the @BodyTypes " + side + " depends on " + option + ", which is not an option");
        }
    }

    /**
     * A body for a value the option cannot have would never hold: the value is one of an enum, or true or false for a
     * boolean, in any case, as tooling compares it with what the endpoint sets.
     */
    private static void checkValue(Class<?> owner, String side, BodyType.When when, BaseOptionModel option) {
        List<String> values = option.getEnums() != null && !option.getEnums().isEmpty()
                ? option.getEnums()
                : "boolean".equals(option.getType()) ? List.of("true", "false") : List.of();
        if (!values.isEmpty() && values.stream().noneMatch(v -> v.equalsIgnoreCase(when.value()))) {
            throw new IllegalArgumentException(
                    owner.getName() + ": the @BodyTypes " + side + " has " + when.option() + "=" + when.value()
                                               + ", which is not a value of the option: " + String.join(", ", values));
        }
    }

    /**
     * A body has a carrier: an option value without one would say nothing. UNCHANGED is for the body after a step, such
     * as a sql query that leaves it unchanged unless it is a select. ANY is for the bodies a step needs. ANY and
     * UNKNOWN stand alone.
     */
    private static void check(Class<?> owner, String side, Carrier[] carriers) {
        if (carriers.length == 0) {
            throw new IllegalArgumentException(owner.getName() + ": the @BodyTypes " + side + " has no carriers");
        }
        List<Carrier> list = Arrays.asList(carriers);
        boolean in = IN.contains(side.split(" ")[0]);
        if (in && list.contains(Carrier.UNCHANGED)) {
            throw new IllegalArgumentException(
                    owner.getName() + ": the @BodyTypes " + side + " is what a step needs, which cannot be unchanged");
        }
        if (!in && list.contains(Carrier.ANY)) {
            throw new IllegalArgumentException(
                    owner.getName() + ": the @BodyTypes " + side + " is the body after a step, which cannot be any");
        }
        if (list.size() > 1 && (list.contains(Carrier.ANY) || list.contains(Carrier.UNKNOWN))) {
            throw new IllegalArgumentException(
                    owner.getName() + ": the @BodyTypes " + side + " has any or unknown with other carriers");
        }
    }

    private static BodyTypeModel asModel(BodyType bodyType) {
        BodyTypeModel model = new BodyTypeModel();
        model.setCarriers(names(bodyType.value()));
        model.setJavaType(emptyAsNull(bodyType.javaType()));
        model.setFormat(emptyAsNull(bodyType.format()));
        model.setDependsOn(new ArrayList<>(Arrays.asList(bodyType.dependsOn())));
        for (BodyType.When when : bodyType.byOption()) {
            BodyTypeModel.OptionBodyTypeModel option = new BodyTypeModel.OptionBodyTypeModel();
            option.setOption(when.option());
            option.setValue(when.value());
            option.setCarriers(names(when.carriers()));
            option.setJavaType(emptyAsNull(when.javaType()));
            model.getByOption().add(option);
        }
        model.setDescription(emptyAsNull(bodyType.description()));
        return model;
    }

    private static List<String> names(Carrier[] carriers) {
        return new ArrayList<>(Arrays.stream(carriers).map(Carrier::getValue).toList());
    }

    private static String emptyAsNull(String value) {
        return value.isEmpty() ? null : value;
    }
}
