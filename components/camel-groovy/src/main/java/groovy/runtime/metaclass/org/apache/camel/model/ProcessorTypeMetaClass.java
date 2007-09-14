/**
 *
 * Copyright 2004 James Strachan
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 **/
package groovy.runtime.metaclass.org.apache.camel.model;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import groovy.lang.MetaClassImpl;
import groovy.lang.MetaClassRegistry;
import org.apache.camel.language.groovy.CamelGroovyMethods;
import org.apache.camel.model.ProcessorType;

/**
 * @version $Revision: 1.1 $
 */
public class ProcessorTypeMetaClass extends MetaClassImpl {
    public ProcessorTypeMetaClass(MetaClassRegistry metaClassRegistry, Class aClass) {
        super(metaClassRegistry, aClass);
    }

    @Override
    public synchronized void initialize() {
        //addNewInstanceMethodsFrom(CamelGroovyMethods.class, theClass);
        addNewInstanceMethodsFrom(CamelGroovyMethods.class, ProcessorType.class);
        super.initialize();
    }

    protected void addNewInstanceMethodsFrom(Class type, Class ownerType) {
        Method[] methods = type.getMethods();
        for (Method method : methods) {
            int modifiers = method.getModifiers();
            if (Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers)) {
                Class<?>[] classes = method.getParameterTypes();
                if (classes.length > 0 && classes[0].equals(ownerType)) {
                    addNewInstanceMethod(method);
                }
            }
        }
    }
}
