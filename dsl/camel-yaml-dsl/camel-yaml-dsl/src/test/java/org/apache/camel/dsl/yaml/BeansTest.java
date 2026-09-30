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
package org.apache.camel.dsl.yaml;

import java.util.Properties;

import org.apache.camel.dsl.yaml.support.YamlTestSupport;
import org.apache.camel.dsl.yaml.support.model.MyBean;
import org.apache.camel.dsl.yaml.support.model.MyBeanBuilder;
import org.apache.camel.dsl.yaml.support.model.MyBuiltBean;
import org.apache.camel.dsl.yaml.support.model.MyCtrBean;
import org.apache.camel.dsl.yaml.support.model.MyDestroyBean;
import org.apache.camel.dsl.yaml.support.model.MyFacBean;
import org.apache.camel.dsl.yaml.support.model.MyFacHelper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BeansTest extends YamlTestSupport {

    @Test
    void beans() throws Exception {
        loadRoutes("""
                    - beans:
                      - name: myNested
                        type: %s
                        properties:
                          field1: 'f1'
                          field2: 'f2'
                          nested:
                            foo: 'nf1'
                            bar: 'nf2'
                      - name: myProps
                        type: %s
                        properties:
                          field1: 'f1_p'
                          field2: 'f2_p'
                          nested.foo: 'nf1_p'
                          nested.bar: 'nf2_p'
                """.formatted(MyBean.class.getName(), MyBean.class.getName()));

        assertThat(context.getRegistry().lookupByName("myNested")).isInstanceOf(MyBean.class);
        MyBean nested = (MyBean) context.getRegistry().lookupByName("myNested");
        assertThat(nested.getField1()).isEqualTo("f1");
        assertThat(nested.getField2()).isEqualTo("f2");
        assertThat(nested.getNested().getFoo()).isEqualTo("nf1");
        assertThat(nested.getNested().getBar()).isEqualTo("nf2");

        assertThat(context.getRegistry().lookupByName("myProps")).isInstanceOf(MyBean.class);
        MyBean props = (MyBean) context.getRegistry().lookupByName("myProps");
        assertThat(props.getField1()).isEqualTo("f1_p");
        assertThat(props.getField2()).isEqualTo("f2_p");
        assertThat(props.getNested().getFoo()).isEqualTo("nf1_p");
        assertThat(props.getNested().getBar()).isEqualTo("nf2_p");
    }

    @Test
    void beansWithPlaceholders() throws Exception {
        Properties initial = new Properties();
        initial.put("p1", "f1");
        initial.put("p2", "f2");
        initial.put("p3", "f3");
        context.getPropertiesComponent().setInitialProperties(initial);

        loadRoutes("""
                    - beans:
                      - name: myNested
                        type: %s
                        properties:
                          field1: '{{p1}}'
                          nested:
                            foo: '{{p2}}'
                      - name: myProps
                        type: %s
                        properties:
                          nested.foo: '{{p3}}'
                """.formatted(MyBean.class.getName(), MyBean.class.getName()));

        assertThat(context.getRegistry().lookupByName("myNested")).isInstanceOf(MyBean.class);
        MyBean nested = (MyBean) context.getRegistry().lookupByName("myNested");
        assertThat(nested.getField1()).isEqualTo("f1");
        assertThat(nested.getNested().getFoo()).isEqualTo("f2");

        assertThat(context.getRegistry().lookupByName("myProps")).isInstanceOf(MyBean.class);
        MyBean props = (MyBean) context.getRegistry().lookupByName("myProps");
        assertThat(props.getNested().getFoo()).isEqualTo("f3");
    }

    @Test
    void beansWithConstructor() throws Exception {
        loadRoutes("""
                    - beans:
                      - name: myCtr
                        type: %s
                        constructors:
                          0: 'f1'
                          1: 'f2'
                        properties:
                          age: 42
                """.formatted(MyCtrBean.class.getName()));

        assertThat(context.getRegistry().lookupByName("myCtr")).isInstanceOf(MyCtrBean.class);
        MyCtrBean ctr = (MyCtrBean) context.getRegistry().lookupByName("myCtr");
        assertThat(ctr.getField1()).isEqualTo("f1");
        assertThat(ctr.getField2()).isEqualTo("f2");
        assertThat(ctr.getAge()).isEqualTo(42);
    }

    @Test
    void beansWithConstructorSorted() throws Exception {
        loadRoutes("""
                    - beans:
                      - name: myCtr
                        type: %s
                        constructors:
                          1: 'f2'
                          0: 'f1'
                        properties:
                          age: 43
                """.formatted(MyCtrBean.class.getName()));

        assertThat(context.getRegistry().lookupByName("myCtr")).isInstanceOf(MyCtrBean.class);
        MyCtrBean ctr = (MyCtrBean) context.getRegistry().lookupByName("myCtr");
        assertThat(ctr.getField1()).isEqualTo("f1");
        assertThat(ctr.getField2()).isEqualTo("f2");
        assertThat(ctr.getAge()).isEqualTo(43);
    }

    @Test
    void beansWithFactory() throws Exception {
        loadRoutes("""
                    - beans:
                      - name: myFac
                        type: %s
                        factoryMethod: createBean
                        constructors:
                          0: 'fac1'
                          1: 'fac2'
                        properties:
                          age: 43
                """.formatted(MyFacBean.class.getName()));

        assertThat(context.getRegistry().lookupByName("myFac")).isInstanceOf(MyFacBean.class);
        MyFacBean fac = (MyFacBean) context.getRegistry().lookupByName("myFac");
        assertThat(fac.getField1()).isEqualTo("fac1");
        assertThat(fac.getField2()).isEqualTo("fac2");
        assertThat(fac.getAge()).isEqualTo(43);
    }

    @Test
    void beansWithFactoryHelper() throws Exception {
        loadRoutes("""
                    - beans:
                      - name: myFac
                        type: %s
                        factoryBean: %s
                        factoryMethod: createBean
                        constructors:
                          0: 'fac1'
                          1: 'fac2'
                        properties:
                          age: 43
                """.formatted(MyFacBean.class.getName(), MyFacHelper.class.getName()));

        assertThat(context.getRegistry().lookupByName("myFac")).isInstanceOf(MyFacBean.class);
        MyFacBean fac = (MyFacBean) context.getRegistry().lookupByName("myFac");
        assertThat(fac.getField1()).isEqualTo("fac1");
        assertThat(fac.getField2()).isEqualTo("fac2");
        assertThat(fac.getAge()).isEqualTo(43);
    }

    @Test
    void beansWithInitDestroy() throws Exception {
        loadRoutes("""
                    - beans:
                      - name: myBean
                        type: %s
                        initMethod: initMe
                        destroyMethod: destroyMe
                        constructors:
                          0: 'fac1'
                          1: 'fac2'
                        properties:
                          age: 43
                """.formatted(MyDestroyBean.class.getName()));

        assertThat(MyDestroyBean.initCalled.get()).isTrue();
        assertThat(MyDestroyBean.destroyCalled.get()).isFalse();

        assertThat(context.getRegistry().lookupByName("myBean")).isInstanceOf(MyDestroyBean.class);
        MyDestroyBean bean = (MyDestroyBean) context.getRegistry().lookupByName("myBean");
        assertThat(bean.getField1()).isEqualTo("fac1");
        assertThat(bean.getField2()).isEqualTo("fac2");
        assertThat(bean.getAge()).isEqualTo(43);

        context.stop();

        assertThat(MyDestroyBean.initCalled.get()).isTrue();
        assertThat(MyDestroyBean.destroyCalled.get()).isTrue();
    }

    @Test
    void beansWithScript() throws Exception {
        loadRoutes("""
                    - beans:
                      - name: myBean
                        type: %s
                        scriptLanguage: groovy
                        script: "var b = new %s(); b.field1 = 'script1'; b.field2 = 'script2'; return b"
                """.formatted(MyBean.class.getName(), MyBean.class.getName()));

        assertThat(context.getRegistry().lookupByName("myBean")).isInstanceOf(MyBean.class);
        MyBean bean = (MyBean) context.getRegistry().lookupByName("myBean");
        assertThat(bean.getField1()).isEqualTo("script1");
        assertThat(bean.getField2()).isEqualTo("script2");
    }

    @Test
    void beansWithScriptWithoutType() throws Exception {
        loadRoutes("""
                    - beans:
                      - name: myBean
                        scriptLanguage: groovy
                        script: "var b = new %s(); b.field1 = 'script1'; b.field2 = 'script2'; return b"
                """.formatted(MyBean.class.getName()));

        assertThat(context.getRegistry().lookupByName("myBean")).isInstanceOf(MyBean.class);
        MyBean bean = (MyBean) context.getRegistry().lookupByName("myBean");
        assertThat(bean.getField1()).isEqualTo("script1");
        assertThat(bean.getField2()).isEqualTo("script2");
    }

    @Test
    void beansWithScriptPropertyPlaceholderDefault() throws Exception {
        context.getPropertiesComponent().addInitialProperty("cheese", "gauda");
        context.getPropertiesComponent().addInitialProperty("cake", "strawberry");
        loadRoutes("""
                    - beans:
                      - name: myBean
                        type: %s
                        scriptLanguage: groovy
                        script: "var b = new %s(); b.field1 = '{{cheese}}'; b.field2 = '{{cake}}'; return b"
                """.formatted(MyBean.class.getName(), MyBean.class.getName()));

        assertThat(context.getRegistry().lookupByName("myBean")).isInstanceOf(MyBean.class);
        MyBean bean = (MyBean) context.getRegistry().lookupByName("myBean");
        assertThat(bean.getField1()).isEqualTo("gauda");
        assertThat(bean.getField2()).isEqualTo("strawberry");
    }

    @Test
    void beansWithScriptPropertyPlaceholderTrue() throws Exception {
        context.getPropertiesComponent().addInitialProperty("cheese", "gauda");
        context.getPropertiesComponent().addInitialProperty("cake", "strawberry");
        loadRoutes("""
                    - beans:
                      - name: myBean
                        type: %s
                        scriptLanguage: groovy
                        scriptPropertyPlaceholders: true
                        script: "var b = new %s(); b.field1 = '{{cheese}}'; b.field2 = '{{cake}}'; return b"
                """.formatted(MyBean.class.getName(), MyBean.class.getName()));

        assertThat(context.getRegistry().lookupByName("myBean")).isInstanceOf(MyBean.class);
        MyBean bean = (MyBean) context.getRegistry().lookupByName("myBean");
        assertThat(bean.getField1()).isEqualTo("gauda");
        assertThat(bean.getField2()).isEqualTo("strawberry");
    }

    @Test
    void beansWithScriptPropertyPlaceholderFalse() throws Exception {
        context.getPropertiesComponent().addInitialProperty("cheese", "gauda");
        context.getPropertiesComponent().addInitialProperty("cake", "strawberry");
        loadRoutes("""
                    - beans:
                      - name: myBean
                        type: %s
                        scriptLanguage: groovy
                        scriptPropertyPlaceholders: false
                        script: "var b = new %s(); b.field1 = '{{cheese}}'; b.field2 = '{{cake}}'; return b"
                """.formatted(MyBean.class.getName(), MyBean.class.getName()));

        assertThat(context.getRegistry().lookupByName("myBean")).isInstanceOf(MyBean.class);
        MyBean bean = (MyBean) context.getRegistry().lookupByName("myBean");
        assertThat(bean.getField1()).isEqualTo("{{cheese}}");
        assertThat(bean.getField2()).isEqualTo("{{cake}}");
    }

    @Test
    void beansWithBuilderClass() throws Exception {
        loadRoutes("""
                    - beans:
                      - name: myBean
                        type: %s
                        builderClass: %s
                        builderMethod: createTheBean
                        properties:
                          field1: builder1
                          field2: builder2
                """.formatted(MyBean.class.getName(), MyBeanBuilder.class.getName()));

        assertThat(context.getRegistry().lookupByName("myBean")).isInstanceOf(MyBean.class);
        MyBean bean = (MyBean) context.getRegistry().lookupByName("myBean");
        assertThat(bean.getField1()).isEqualTo("builder1");
        assertThat(bean.getField2()).isEqualTo("builder2");
    }

    @Test
    void beansWithInferredBuilder() throws Exception {
        loadRoutes("""
                    - beans:
                      - name: chatModel
                        type: %s
                        properties:
                          baseUrl: http://localhost:11434
                          modelName: qwen2.5
                          timeout: 2m
                          label: support
                """.formatted(MyBuiltBean.class.getName()));

        assertThat(context.getRegistry().lookupByName("chatModel")).isInstanceOf(MyBuiltBean.class);
        MyBuiltBean bean = (MyBuiltBean) context.getRegistry().lookupByName("chatModel");
        assertThat(bean.getBaseUrl()).isEqualTo("http://localhost:11434");
        assertThat(bean.getModelName()).isEqualTo("qwen2.5");
        assertThat(bean.getTimeout()).isEqualTo(java.time.Duration.ofMinutes(2));
        assertThat(bean.getLabel()).isEqualTo("support");
    }

    @Test
    void beansWithInferredBuilderAndUnknownPropertyFailsWithThePropertyName() {
        Exception e = assertThrows(Exception.class, () -> loadRoutes("""
                    - beans:
                      - name: chatModel
                        type: %s
                        properties:
                          model: qwen2.5
                """.formatted(MyBuiltBean.class.getName())));

        String msg = messages(e);
        assertThat(msg.contains("model=qwen2.5")).isTrue();
        assertThat(msg.contains("The bean is created through its builder " + MyBuiltBean.Builder.class.getName()
                                + ", which accepts: baseUrl, "))
                .isTrue();
        assertThat(msg.contains("modelName, timeout; the created bean accepts: label")).isTrue();
    }

    @Test
    void beansClassNotFoundInWrongPackageSaysDidYouMean() {
        Exception e = assertThrows(Exception.class, () -> loadRoutes("""
                    - beans:
                      - name: myAgg
                        type: com.foo.UseLatestAggregationStrategy
                """));

        String msg = messages(e);
        assertThat(msg.contains("Error creating bean: myAgg of type: #class:com.foo.UseLatestAggregationStrategy")).isTrue();
        assertThat(msg.contains("class com.foo.UseLatestAggregationStrategy was not found")).isTrue();
        assertThat(msg.contains(
                "did you mean org.apache.camel.processor.aggregate.UseLatestAggregationStrategy (org.apache.camel.AggregationStrategy)?"))
                .isTrue();
        assertThat(msg.contains("write: type: org.apache.camel.processor.aggregate.UseLatestAggregationStrategy")).isTrue();
    }

    @Test
    void beansClassNotFoundWithoutPackageSaysDidYouMean() {
        Exception e = assertThrows(Exception.class, () -> loadRoutes("""
                    - beans:
                      - name: myRepo
                        type: MemoryAggregationRepository
                """));

        String msg = messages(e);
        assertThat(msg.contains("class MemoryAggregationRepository was not found")).isTrue();
        assertThat(msg.contains(
                "did you mean org.apache.camel.processor.aggregate.MemoryAggregationRepository (org.apache.camel.spi.AggregationRepository)?"))
                .isTrue();
    }

    @Test
    void beansClassNotFoundThatIsNotABuiltInBeanKeepsTheGenericHint() {
        Exception e = assertThrows(Exception.class, () -> loadRoutes("""
                    - beans:
                      - name: myBean
                        type: com.foo.MyBean
                """));

        String msg = messages(e);
        assertThat(msg.contains(
                "class com.foo.MyBean was not found (check the package name; a class from another library needs its dependency added)"))
                .isTrue();
        assertThat(msg.contains("did you mean")).isFalse();
    }

    private static String messages(Throwable e) {
        StringBuilder sb = new StringBuilder();
        for (Throwable t = e; t != null; t = t.getCause()) {
            sb.append(t.getMessage()).append('\n');
        }
        return sb.toString();
    }
}
