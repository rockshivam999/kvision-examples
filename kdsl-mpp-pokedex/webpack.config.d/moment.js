try {
    const moment = require("moment");

    config.plugins.push(new webpack.ProvidePlugin({
        moment: "moment",
        "window.moment": "moment"
    }));
} catch (e) {
}
