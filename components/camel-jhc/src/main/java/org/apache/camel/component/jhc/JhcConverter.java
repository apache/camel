package org.apache.camel.component.jhc;

import org.apache.camel.Converter;
import org.apache.http.HttpEntity;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;

/**
 * Created by IntelliJ IDEA.
 * User: gnodet
 * Date: Sep 10, 2007
 * Time: 8:26:44 AM
 * To change this template use File | Settings | File Templates.
 */
@Converter
public final class JhcConverter {

    private JhcConverter() {
    }

    @Converter
    public static InputStream toInputStream(HttpEntity entity) throws IOException {
        return entity.getContent();
    }

    @Converter
    public static byte[] toByteArray(HttpEntity entity) throws IOException {
        return EntityUtils.toByteArray(entity);
    }

    @Converter
    public static String toString(HttpEntity entity) throws IOException {
        return EntityUtils.toString(entity);
    }

    @Converter
    public static HttpEntity toEntity(InputStream is) {
        return new InputStreamEntity(is, -1);
    }

    @Converter
    public static HttpEntity toEntity(String str) throws UnsupportedEncodingException {
        return new StringEntity(str);
    }
}
