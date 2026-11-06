// Dev-server proxy so the browser build can read Google News RSS without CORS errors.
// Requests to /gnews/* are forwarded to https://news.google.com/*.
config.devServer = config.devServer || {};
config.devServer.proxy = [
    {
        context: ["/gnews"],
        target: "https://news.google.com",
        changeOrigin: true,
        secure: true,
        pathRewrite: { "^/gnews": "" },
    },
];
