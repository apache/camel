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
package org.apache.camel.maven;

import org.apache.camel.util.ObjectHelper;
import org.apache.camel.util.ReportingTypeConverterLoader;
import org.apache.camel.util.ReportingTypeConverterRegistry;
import org.apache.camel.util.ReportingTypeConverterLoader.TypeMapping;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

/**
 * Generate report of available type converstions.
 *
 * @goal report
 * 
 * @phase verify
 */
public class ConvertersMojo
    extends AbstractMojo
{
    /**
     * Base directory where all reports are written to.
     * 
     * @parameter expression="${project.build.directory}/camel-reports"
     */
    private File reportsDirectory;

    /**
     * Convenience parameter that allows you to disable report generation.
     *
     * @parameter expression="${generateReports}" default-value="true"
     */
    private boolean generateReports;

    private static final String LINE_SEPARATOR = 
    	"-------------------------------------------------------------------------------\n";
    
    public void execute() throws MojoExecutionException {
        getLog().info( "Camel report directory: " + reportsDirectory );
        
        ReportingTypeConverterLoader loader = new ReportingTypeConverterLoader();
        ReportingTypeConverterRegistry registry = new ReportingTypeConverterRegistry();
        try {
        	loader.load(registry);
        	
        	String[] errors = registry.getErrors();
        	for (String error : errors) {
        		getLog().error(error);
        	}
        }
        catch (Exception e) {
        	throw new MojoExecutionException(e.getMessage());
        }

    	if (generateReports) {
        	generateReport(loader.getTypeConversions());
    	}
    }
    
    protected void generateReport(TypeMapping[] mappings) throws MojoExecutionException {
    	
    	File f = reportsDirectory;
        if (!f.exists()) {
            f.mkdirs();
        }

        File report = new File(f, "camel-converters.txt" );
        FileWriter fw = null;
        try
        {
            fw = new FileWriter(report);

            fw.write("Camel Type Converter definitions\n");
            fw.write(LINE_SEPARATOR);

            Set<String> packages = new HashSet<String>();
            Set<String> classes = new HashSet<String>();
            
            StringBuffer buffer = new StringBuffer();
            Class prevFrom = null;
            Class prevTo = null;
            for (TypeMapping mapping : mappings) {
            	boolean ignored = false;
            	Class from = mapping.getFromType();
            	Class to = mapping.getToType();
            	if (ObjectHelper.equals(from, prevFrom) &&
            		ObjectHelper.equals(to, prevTo)) {
            		
            		ignored = true;
            		buffer.append(" ");
            	}
            	prevFrom = from;
            	prevTo = to;
            	Method method = mapping.getMethod();
            	Class methodClass = method.getDeclaringClass();
            	packages.add(methodClass.getPackage().getName());
            	classes.add(methodClass.getName());
            	
            	buffer.append(from.getSimpleName() + "=>" + to.getSimpleName());
            	buffer.append(" [" + mapping.getConverterType().getSimpleName());
            	buffer.append("(" + methodClass.getName() + "." + method.getName() + "())]");
            	if (ignored) {
                	buffer.append(" - IGNORED replaced by conversion method above");
            	}
            	
                fw.write(buffer.toString() + "\n");
                buffer.setLength(0);
            }
            
            String summary = "Found " + mappings.length + " type conversion methods in " 
            		+ classes.size() + " classes from " + packages.size() + " packages."; 
            fw.write(LINE_SEPARATOR);
            fw.write(summary + "\n");
    		getLog().info(summary);
        }
        catch (IOException e ) {
            throw new MojoExecutionException( "Error creating report file " + report, e);
        }
        finally {
            if (fw != null) {
                try {
                    fw.close();
                }
                catch (IOException e) {
                    // ignore
                }
            }
        }
    }
}
