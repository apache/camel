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
package org.apache.camel.spring.javaconfig;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.apache.camel.util.ObjectHelper;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.AbstractApplicationContext;

/**
 * The Main class which takes the spring java config parameter
 */
public class Main extends org.apache.camel.spring.Main {
    
    private String basedPackages;
    private String configClasses;
    private Class[] configClass;

    public Main() {

        addOption(new ParameterOption("bp", "basedPackages",
            "Sets the based packages of Spring java config ApplicationContext", "basedPackages") {
            protected void doProcess(String arg, String parameter, LinkedList<String> remainingArgs) {
                setBasedPackages(parameter);
            }
        });

        addOption(new ParameterOption("cc", "configClasses",
            "Sets the config of Spring java config ApplicationContext", "configureClasses") {
            protected void doProcess(String arg, String parameter, LinkedList<String> remainingArgs) {
                setConfigClasses(parameter);
            }
        });
    }
    
    public static void main(String... args) throws Exception {
        Main main = new Main();
        instance = main;
        main.run(args);
    }

    /**
     * Sets the base packages where Spring annotation scanning is performed.
     * You can separate multiple packages using comma or semi colon.
     */
    public void setBasedPackages(String config) {
        basedPackages = config;
    }

    public String getBasedPackages() {
        return basedPackages;
    }

    /**
     * Sets the name of Spring <tt>@Configuration</tt> classes to use.
     * You can separate multiple classes using comma or semi colon.
     */
    public void setConfigClasses(String config) {
        configClasses = config;
    }
    
    public String getConfigClasses() {
        return configClasses;
    }

    /**
     * @deprecated use {@link #setConfigClasses(String)}
     */
    @Deprecated
    public void setConfigClassesString(String config) {
        setConfigClasses(config);
    }

    /**
     * @deprecated use {@link #getConfigClasses()}
     */
    @Deprecated
    public String getConfigClassesString() {
        return getConfigClasses();
    }

    public Class[] getConfigClass() {
        return configClass;
    }

    /**
     * Sets the Spring <tt>@Configuration</tt> classes to use.
     */
    public void setConfigClass(Class... configClass) {
        this.configClass = configClass;
    }

    private Class<?>[] getConfigClasses(String configureClasses) {
        List<Class<?>> answer = new ArrayList<Class<?>>();
        String[] classes =  configureClasses.split("(;|,)");
        for (String className :  classes) {
            className = className.trim();
            Class<?> configClass = ObjectHelper.loadClass(className);
            if (configClass != null) {
                answer.add(configClass);
            } 
        }
        return answer.toArray(new Class<?>[answer.size()]);
    }
        
    protected AbstractApplicationContext createDefaultApplicationContext() {
        ApplicationContext parentContext = getParentApplicationContext();
        AnnotationConfigApplicationContext acApplicationContext = new AnnotationConfigApplicationContext();
        if (parentContext != null) {
            acApplicationContext.setParent(parentContext);
        }
        if (getConfigClasses() != null) {
            Class<?>[] configClasses = getConfigClasses(getConfigClasses());
            for (Class<?> cls : configClasses) {
                acApplicationContext.register(cls);
            }
        }
        if (getConfigClass() != null) {
            for (Class<?> cls : getConfigClass()) {
                acApplicationContext.register(cls);
            }
        }
        if (getBasedPackages() != null) {
            String[] basePackages = getBasedPackages().split("(;|,)");
            for (String basePackage : basePackages) {
                basePackage = basePackage.trim();
                acApplicationContext.scan(basePackage);
            }
        }
        acApplicationContext.refresh();
        return acApplicationContext;
    }

}
