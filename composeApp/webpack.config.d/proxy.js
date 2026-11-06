// Dev-server proxy so the browser build can read news feeds without CORS errors.
// Requests to /proxy/<host>/<path> are forwarded to https://<host>/<path>
// (Bing News, Hacker News search, GitHub changelog/release feeds, and any custom feed URLs).
const HOST = /^[a-z0-9.-]+\.[a-z]{2,}$/i;
const BLOCKED = /^(localhost|.*\.local|.*\.internal|\d+\.\d+\.\d+\.\d+)$/i;

function targetHost(url) {
    const host = (url.split("/")[2] || "").toLowerCase();
    if (!HOST.test(host) || BLOCKED.test(host)) throw new Error("Blocked proxy host: " + host);
    return host;
}

config.devServer = config.devServer || {};
config.devServer.proxy = [
    {
        context: ["/proxy/"],
        target: "https://www.bing.com",
        router: (req) => "https://" + targetHost(req.url),
        pathRewrite: (path) => "/" + path.split("/").slice(3).join("/"),
        changeOrigin: true,
        secure: true,
        followRedirects: true,
        // Bing serves HTML instead of RSS to mobile User-Agents, so mirror the Android app's neutral UA.
        headers: { "User-Agent": "Mozilla/5.0 (compatible; AINewsBroker/0.1)" },
    },
];
