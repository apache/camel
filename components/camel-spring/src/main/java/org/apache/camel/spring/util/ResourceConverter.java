package org.apache.camel.spring.util;

import java.io.IOException;
import java.io.InputStream;

import org.apache.camel.Converter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;

/**
 * A simple {@linkplain Converter} for Springs {@linkplain Resource} class.
 * 
 * @author <a href="mailto:david@davidkarlsen.com">David J. M. Karlsen</a>
 *
 */
@Converter
public class ResourceConverter
{
    private static final Logger LOG = LoggerFactory.getLogger( ResourceConverter.class );
   
    /**
     * Converts from Springs {@linkplain Resource} to {@linkplain InputStream}.
     * @param resource a resource, may be null.
     * @return null if input is null, else the resource params {@linkplain InputStream}.
     * @throws IOException
     */
    @Converter
    public static InputStream convert( Resource resource ) throws IOException {
        LOG.trace( "Converting resource {}", resource );
        
        return resource == null ? null : resource.getInputStream();
    }
}
