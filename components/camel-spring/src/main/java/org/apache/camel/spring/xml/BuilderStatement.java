/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.spring.xml;

import java.util.ArrayList;

import org.springframework.beans.factory.BeanFactory;

public class BuilderStatement {
	private ArrayList<BuilderAction> actions;
	private Class returnType;

	public Object create(BeanFactory beanFactory, Object rootBuilder) {
		Object currentBuilder = rootBuilder;
		BuilderAction lastAction=null;
		for (BuilderAction action : actions) {
			// The last action may have left us without a builder to invoke next!
			if( currentBuilder == null ) {
				throw new IllegalArgumentException("Invalid configuration.  The '"+lastAction.getName()+"' action cannot be followed by the '"+action.getName()+"' action.");
			}
			currentBuilder = action.invoke(beanFactory, rootBuilder, currentBuilder);
			lastAction = action;
		}
		return currentBuilder;
	}

	public ArrayList<BuilderAction> getActions() {
		return actions;
	}
	public void setActions(ArrayList<BuilderAction> actions) {
		this.actions = actions;
	}

	public Class getReturnType() {
		return returnType;
	}
	public void setReturnType(Class returnType) {
		this.returnType = returnType;
		
	}

}
