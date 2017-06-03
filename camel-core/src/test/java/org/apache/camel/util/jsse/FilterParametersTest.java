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
package org.apache.camel.util.jsse;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.camel.CamelContext;

public class FilterParametersTest extends AbstractJsseParametersTest {
    
    public void testPropertyPlaceholders() throws Exception {
        CamelContext context = this.createPropertiesPlaceholderAwareContext();
        
        FilterParameters filter = new FilterParameters();
        filter.setCamelContext(context);
        filter.getInclude().add("{{filterParameters.include}}");
        filter.getExclude().add("{{filterParameters.exclude}}");
        
        FilterParameters.Patterns patterns = filter.getPatterns();
        
        List<Pattern> includes = patterns.getIncludes();
        List<Pattern> excludes = patterns.getExcludes();
        
        assertNotNull(includes);
        assertNotNull(excludes);
        
        assertEquals(1, includes.size());
        assertEquals(1, excludes.size());
        
        Matcher includeMatcher = includes.get(0).matcher("include");
        assertTrue(includeMatcher.matches());
        
        Matcher excludeMatcher = excludes.get(0).matcher("exclude");
        assertTrue(excludeMatcher.matches());
    }
    
    public void testGetIncludePatterns() {
        FilterParameters filter = new FilterParameters();
        filter.getInclude().add("asdfsadfsadfsadf");
        
        List<Pattern> includes = filter.getIncludePatterns();
        List<Pattern> excludes = filter.getExcludePatterns();
        
        assertNotNull(includes);
        assertEquals(1, includes.size());
        
        assertNotNull(excludes);
        assertEquals(0, excludes.size());
        
        assertNotNull(includes.get(0));
        
        Matcher matcher = includes.get(0).matcher("asdfsadfsadfsadf");
        assertTrue(matcher.matches());
    }
    
    public void testGetExcludePatterns() {
        FilterParameters filter = new FilterParameters();
        filter.getExclude().add("asdfsadfsadfsadf");
        
        List<Pattern> includes = filter.getIncludePatterns();
        List<Pattern> excludes = filter.getExcludePatterns();
        
        assertNotNull(excludes);
        assertEquals(1, excludes.size());
        
        assertNotNull(includes);
        assertEquals(0, includes.size());
        
        assertNotNull(excludes.get(0));
        
        Matcher matcher = excludes.get(0).matcher("asdfsadfsadfsadf");
        assertTrue(matcher.matches());
    }
    
    public void test() {
        FilterParameters filter = new FilterParameters();
        filter.getInclude().add("asdf.*");
        filter.getExclude().add("aa");
        
        FilterParameters.Patterns patterns = filter.getPatterns();
        
        List<Pattern> includes = patterns.getIncludes();
        List<Pattern> excludes = patterns.getExcludes();
        
        assertNotNull(includes);
        assertNotNull(excludes);
        
        assertEquals(1, includes.size());
        assertEquals(1, excludes.size());
        
        Matcher includeMatcher = includes.get(0).matcher("asdfsadfsadfsadf");
        assertTrue(includeMatcher.matches());
        
        Matcher excludeMatcher = excludes.get(0).matcher("aa");
        assertTrue(excludeMatcher.matches());
        
    }
}
