package org.apache.camel.eclipse;

import org.openarchitectureware.xtext.AbstractXtextEditorPlugin;
import org.openarchitectureware.xtext.LanguageUtilities;
import org.osgi.framework.BundleContext;

public class camelEditorPlugin extends AbstractXtextEditorPlugin {
   private static camelEditorPlugin plugin;
   public static camelEditorPlugin getDefault() {
      return plugin;
   }

   private camelUtilities utilities = new camelUtilities();
   public LanguageUtilities getUtilities() {
      return utilities;
   }

   public camelEditorPlugin() {
      plugin = this;
   }

   public void stop(BundleContext context) throws Exception {
      super.stop(context);
      plugin = null;
   }
}
