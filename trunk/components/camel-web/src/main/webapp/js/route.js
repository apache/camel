var _editorComponent;

// Loads and configures the objects that the editor needs
dojo.addOnLoad(function() {
  _editorComponent = new bespin.editor.Component('editor', {
    syntax: "xml",
    loadfromdiv: true
  });
});

function submitRoute() {
  dojo.byId('route').value = _editorComponent.getContent();
  document.forms["routeForm"].submit();
}

