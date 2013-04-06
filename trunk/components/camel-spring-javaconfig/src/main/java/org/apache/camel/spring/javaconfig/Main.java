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
    
    private String configClassesString;
    
    public Main() {

        addOption(new ParameterOption("bp", "basedPackages",
            "Sets the based packages of spring java config ApplicationContext", "basedPackages") {
            protected void doProcess(String arg, String parameter, LinkedList<String> remainingArgs) {
                setBasedPackages(parameter);
            }
        });

        addOption(new ParameterOption("cc", "configClasses",
            "Sets the config Class of spring java config ApplicationContext", "configureClasses") {
            protected void doProcess(String arg, String parameter, LinkedList<String> remainingArgs) {
                setConfigClassesString(parameter);
            }
        });
    }
    
    public static void main(String... args) throws Exception {
        Main main = new Main();
        instance = main;
        main.enableHangupSupport();
        main.run(args);
    }
    
    public void setBasedPackages(String config) {
        basedPackages = config;
    }
    
    public String getBasedPackages() {
        return basedPackages;
    }
    
    public void setConfigClassesString(String config) {
        configClassesString = config;
    }
    
    public String getConfigClassesString() {
        return configClassesString;
    }
    
    private Class<?>[] getConfigClasses(String configureClasses) {
        List<Class<?>> answer = new ArrayList<Class<?>>();
        String[] classes =  configureClasses.split(";");
        for (String className :  classes) {
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
        if (getConfigClassesString() != null) {
            Class<?>[] configClasses = getConfigClasses(getConfigClassesString());
            for (Class<?> cls : configClasses) {
                acApplicationContext.register(cls);
            }
        }
        if (getBasedPackages() != null) {
            String[] basePackages = getBasedPackages().split(";");
            for (String basePackage : basePackages) {
                acApplicationContext.scan(basePackage);
            }
        }
        acApplicationContext.refresh();
        return acApplicationContext;
        
    }

}
