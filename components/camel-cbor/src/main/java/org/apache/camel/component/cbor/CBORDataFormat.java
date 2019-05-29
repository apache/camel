package org.apache.camel.component.cbor;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Set;

import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.spi.annotations.Dataformat;
import org.apache.camel.support.service.ServiceSupport;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.CollectionType;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;

@Dataformat("cbor")
public class CBORDataFormat extends ServiceSupport implements DataFormat, DataFormatName {

    private CamelContext camelContext;
	private ObjectMapper objectMapper;
    private Class<?> unmarshalType;
    private boolean useDefaultObjectMapper = true;
    private boolean allowUnmarshallType;
    private Class<? extends Collection> collectionType;
    private boolean useList;
    
    /**
     * Use the default CBOR Jackson {@link ObjectMapper} and {@link Object}
     */
    public CBORDataFormat() {
    }
    
    /**
     * Use the default CBOR Jackson {@link ObjectMapper} and with a custom unmarshal
     * type
     *
     * @param unmarshalType the custom unmarshal type
     */
    public CBORDataFormat(ObjectMapper objectMapper, Class<?> unmarshalType) {
        this.unmarshalType = unmarshalType;
        this.objectMapper = objectMapper;
    }
	
	@Override
	public void marshal(Exchange exchange, Object graph, OutputStream stream) throws Exception {
		stream.write(this.objectMapper.writeValueAsBytes(graph));
	}

	@Override
	public Object unmarshal(Exchange exchange, InputStream stream) throws Exception {
		Class<?> clazz = unmarshalType;
        String type = null;
        if (allowUnmarshallType) {
            type = exchange.getIn().getHeader(CBORConstants.UNMARSHAL_TYPE, String.class);
        }
        if (type != null) {
            clazz = exchange.getContext().getClassResolver().resolveMandatoryClass(type);
        }
        if (collectionType != null) {
            CollectionType collType = objectMapper.getTypeFactory().constructCollectionType(collectionType, clazz);
            return this.objectMapper.readValue(stream, collType);
        } else {
            return this.objectMapper.readValue(stream, clazz);
        }
	}

	@Override
	public String getDataFormatName() {
		return "cbor";
	}

	public ObjectMapper getObjectMapper() {
		return objectMapper;
	}

	public void setObjectMapper(ObjectMapper objectMapper) {
		this.objectMapper = objectMapper;
	}

	public Class<?> getUnmarshalType() {
		return unmarshalType;
	}

	public void setUnmarshalType(Class<?> unmarshalType) {
		this.unmarshalType = unmarshalType;
	}

	public boolean isAllowUnmarshallType() {
		return allowUnmarshallType;
	}

	public void setAllowUnmarshallType(boolean allowUnmarshallType) {
		this.allowUnmarshallType = allowUnmarshallType;
	}

	public Class<? extends Collection> getCollectionType() {
		return collectionType;
	}

	public void setCollectionType(Class<? extends Collection> collectionType) {
		this.collectionType = collectionType;
	}

	public boolean isUseList() {
		return useList;
	}

	public void setUseList(boolean useList) {
		this.useList = useList;
	}
	
    public boolean isUseDefaultObjectMapper() {
		return useDefaultObjectMapper;
	}

	public void setUseDefaultObjectMapper(boolean useDefaultObjectMapper) {
		this.useDefaultObjectMapper = useDefaultObjectMapper;
	}

	/**
     * Uses {@link java.util.ArrayList} when unmarshalling.
     */
    public void useList() {
        setCollectionType(ArrayList.class);
    }

    /**
     * Uses {@link java.util.HashMap} when unmarshalling.
     */
    public void useMap() {
        setCollectionType(null);
        setUnmarshalType(HashMap.class);
    }

    @Override
    protected void doStart() throws Exception {
        if (objectMapper == null) {
            // lookup if there is a single default mapper we can use
            if (useDefaultObjectMapper && camelContext != null) {
                Set<ObjectMapper> set = camelContext.getRegistry().findByType(ObjectMapper.class);
                if (set.size() == 1) {
                    objectMapper = set.iterator().next();
                    log.info("Found single ObjectMapper in Registry to use: {}", objectMapper);
                } else if (set.size() > 1) {
                    log.debug("Found {} ObjectMapper in Registry cannot use as default as there are more than one instance.", set.size());
                }
            }
            if (objectMapper == null) {
            	CBORFactory factory = new CBORFactory();
                objectMapper = new ObjectMapper(factory);
                log.debug("Creating new ObjectMapper to use: {}", objectMapper);
            }
        }

        if (useList) {
            setCollectionType(ArrayList.class);
        }
    }

	@Override
	protected void doStop() throws Exception {
		// TODO Auto-generated method stub
		
	}
}
