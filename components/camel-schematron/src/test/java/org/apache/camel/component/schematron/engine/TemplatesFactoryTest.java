package org.apache.camel.component.schematron.engine;

import org.junit.Assert;
import org.junit.Test;

import javax.xml.transform.Templates;

/**
 * Created by akhettar on 22/12/2013.
 */
public class TemplatesFactoryTest {

    private String rules = "sch/sample-schematron.sch";


    @Test
    public void testInstantiateAnInstanceOfTemplates() throws Exception {


        TemplatesFactory fac = TemplatesFactory.newInstance(ClassLoader.getSystemResourceAsStream(rules));
        Templates templates = fac.newTemplates();
        Assert.assertNotNull(templates);


    }
}
