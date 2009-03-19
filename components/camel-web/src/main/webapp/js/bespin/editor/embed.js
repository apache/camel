(function() {
    // -- Load Script
    var loadScript = function(src) {
        var embedscript = document.createElement("script");
        embedscript.type = "text/javascript";
        embedscript.src = src;
        document.getElementsByTagName("head")[0].appendChild(embedscript);
        // document.write("<scr"+"ipt type=\"text/javascript\" src=\""+ src +"\">"+"</scr"+"ipt>");
    }
    // -- If Dojo hasn't been installed yet, get to it
    if (typeof window.dojo == "undefined") {
        loadScript("../../js/dojo/dojo.js");
    }

    // -- Load up the embeddable editor component
    dojo.require("bespin.editor.Component");
})();