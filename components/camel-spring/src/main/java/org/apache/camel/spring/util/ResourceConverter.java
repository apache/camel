package org.apache.camel.spring.util;

import java.io.IOException;
import java.io.InputStream;

import org.apache.camel.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;

@Converter
public class ResourceConverter
{
    private static final Logger LOG = LoggerFactory.getLogger( ResourceConverter.class );
    
    @Converter
    public static InputStream convert( Resource resource ) throws IOException {
        LOG.trace( "Converting resource {}", resource );
        
        return resource == null ? null : resource.getInputStream();
    }
}
