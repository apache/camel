package org.apache.camel.impl;

import com.github.jasminb.jsonapi.JSONAPIDocument;
import com.github.jasminb.jsonapi.ResourceConverter;
import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;
import org.apache.camel.spi.DataFormatName;
import org.apache.camel.spi.annotations.Dataformat;
import org.apache.camel.support.service.ServiceSupport;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * JsonApi {@link org.apache.camel.spi.DataFormat} for marshal/unmarshal
 */
@Dataformat("jsonApi")
public class JsonApiDataFormat extends ServiceSupport implements DataFormat, DataFormatName {

    private Class<?>[] dataFormatTypes;
    private Class<?> mainFormatType;

    @Override
    public String getDataFormatName() {
        return "jsonApi";
    }

    /**
     * Marshals the object to the given Stream.
     *
     * @param exchange
     *            the current exchange
     * @param graph
     *            the object to be marshalled
     * @param stream
     *            the output stream to write the marshalled result to
     * @throws Exception
     *             can be thrown
     */
    @Override
    public void marshal(Exchange exchange, Object graph, OutputStream stream) throws Exception {
        ResourceConverter converter = new ResourceConverter(dataFormatTypes);
        byte[] objectAsBytes = converter.writeDocument(new JSONAPIDocument<>(graph));
        stream.write(objectAsBytes);
    }

    /**
     * Unmarshals the given Json API stream into an object.
     * <p/>
     * <b>Notice:</b> The result is set as body on the exchange OUT message. It is possible to mutate the OUT message provided in the given exchange
     * parameter. For instance adding headers to the OUT message will be preserved.
     * <p/>
     * It's also legal to return the <b>same</b> passed <tt>exchange</tt> as is but also a {@link Message} object as well which will be used as the
     * OUT message of <tt>exchange</tt>.
     *
     * @param exchange
     *            the current exchange
     * @param stream
     *            the input stream with the object to be unmarshalled
     * @return the unmarshalled object
     * @throws Exception
     *             can be thrown
     */
    @Override
    public Object unmarshal(Exchange exchange, InputStream stream) throws Exception {
        ResourceConverter converter = new ResourceConverter(dataFormatTypes);
        JSONAPIDocument<?> jsonApiDocument = converter.readDocument(stream, mainFormatType);
        return jsonApiDocument.get();
    }

    @Override
    protected void doStart() throws Exception {
        // noop
    }

    @Override
    protected void doStop() throws Exception {
        // noop
    }

    public void setDataFormatTypes(Class<?>[] dataFormatTypes) {
        this.dataFormatTypes = dataFormatTypes;
    }

    public void setMainFormatType(Class<?> mainFormatType) {
        this.mainFormatType = mainFormatType;
    }
}
