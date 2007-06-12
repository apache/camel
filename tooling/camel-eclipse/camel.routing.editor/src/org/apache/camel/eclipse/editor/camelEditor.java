package org.apache.camel.eclipse.editor;

import org.openarchitectureware.xtext.AbstractXtextEditorPlugin;
import org.openarchitectureware.xtext.editor.AbstractXtextEditor;

import org.apache.camel.eclipse.camelEditorPlugin;

public class camelEditor extends AbstractXtextEditor {

   protected AbstractXtextEditorPlugin getPlugin() {
      return camelEditorPlugin.getDefault();
   }
}
