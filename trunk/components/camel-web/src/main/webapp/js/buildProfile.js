dependencies = {
    layers: [
        {
            name: "../index_all.js",
            dependencies: [
                "bespin.page.index.dependencies"
            ]
        },
        {
            name: "../editor_all.js",
            dependencies: [
                "bespin.page.editor.dependencies"
            ]
        },
        {
            name: "../dashboard_all.js",
            dependencies: [
                "bespin.page.dashboard.dependencies"
            ]
        }
    ],
    prefixes: [
        ["dijit", "../dijit"],
        ["dojox", "../dojox"],
        ["bespin", "../bespin"],
        ["th", "../th"]
    ]
};
