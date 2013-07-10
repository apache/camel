package org.apache.camel.spring.util;

import java.io.IOException;
import java.io.InputStream;

import org.apache.camel.CamelContext;
import org.apache.camel.TestSupport;
import org.apache.camel.TypeConverter;
import org.apache.camel.converter.IOConverter;
import org.apache.camel.impl.DefaultCamelContext;
import org.junit.Assert;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;

public class ResourceConverterTest
    extends TestSupport
{
    //@Test
    public void testResourceConverterRegistry() {
        Assert.assertNotNull( getResourceTypeConverter() );
    }
    
    private TypeConverter getResourceTypeConverter() {
        CamelContext camelContext = new DefaultCamelContext();
        TypeConverter typeConverter = camelContext.getTypeConverterRegistry().lookup( InputStream.class, Resource.class );
        
        return typeConverter;
    }
    
    public void testNullConversion() {
        InputStream inputStream = getResourceTypeConverter().convertTo( InputStream.class, null );
        Assert.assertNull( inputStream );
    }
    
    public void testNonNullConversion() throws IOException {
        Resource resource = new ClassPathResource( "testresource.txt", ResourceConverterTest.class );
        Assert.assertTrue( resource.exists() );
        InputStream inputStream = getResourceTypeConverter().convertTo( InputStream.class, resource );
        byte[] resourceBytes = IOConverter.toBytes( resource.getInputStream() );
        byte[] inputStreamBytes = IOConverter.toBytes( inputStream );
        Assert.assertArrayEquals( resourceBytes, inputStreamBytes );
    }
    
    
    
    
}
