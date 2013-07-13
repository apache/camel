xquery version "1.0" encoding "UTF-8";

(: the prefix declaration for our custom extension :)
declare namespace efx = "http://test/saxon/ext";

<transformed extension-function-render="{efx:simple(/body/text())}" />