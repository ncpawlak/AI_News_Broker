// Dev-server proxy so the browser build can read Bing News RSS without CORS errors.
// Requests to /bing/* are forwarded to https://www.bing.com/*.
config.devServer = config.devServer || {};
config.devServer.proxy = [
    {
        context: ["/bing"],
        target: "https://www.bing.com",
        changeOrigin: true,
        secure: true,
        // Bing serves HTML instead of RSS to mobile User-Agents, so mirror the Android app's neutral UA.
        headers: { "User-Agent": "Mozilla/5.0 (compatible; AINewsBroker/0.1)" },
        pathRewrite: { "^/bing": "" },
    },
];
