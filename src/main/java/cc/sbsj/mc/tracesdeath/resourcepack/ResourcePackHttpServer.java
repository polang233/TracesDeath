package cc.sbsj.mc.tracesdeath.resourcepack;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.*;

/** Serves only the selected bundled archive; no filesystem paths are exposed. */
public final class ResourcePackHttpServer implements AutoCloseable {
    private final HttpServer server;
    private final ExecutorService executor;

    public ResourcePackHttpServer(String address, int port, java.util.Map<String, byte[]> archives)
            throws IOException {
        server = HttpServer.create(new InetSocketAddress(address, port), 16);
        executor =
                new ThreadPoolExecutor(
                        2,
                        2,
                        0L,
                        TimeUnit.MILLISECONDS,
                        new ArrayBlockingQueue<Runnable>(32),
                        task -> {
                            Thread thread = new Thread(task, "TracesDeath-ResourcePack");
                            thread.setDaemon(true);
                            return thread;
                        },
                        new ThreadPoolExecutor.AbortPolicy());
        server.setExecutor(executor);
        server.createContext(
                "/",
                exchange -> {
                    try {
                        byte[] archive = archives.get(exchange.getRequestURI().getPath());
                        if (archive == null) {
                            exchange.sendResponseHeaders(404, -1);
                            return;
                        }
                        String method = exchange.getRequestMethod();
                        if (!"GET".equals(method) && !"HEAD".equals(method)) {
                            exchange.getResponseHeaders().set("Allow", "GET, HEAD");
                            exchange.sendResponseHeaders(405, -1);
                            return;
                        }
                        exchange.getResponseHeaders().set("Content-Type", "application/zip");
                        exchange.getResponseHeaders()
                                .set("Content-Length", Integer.toString(archive.length));
                        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
                        if ("HEAD".equals(method)) exchange.sendResponseHeaders(200, -1);
                        else {
                            exchange.sendResponseHeaders(200, archive.length);
                            exchange.getResponseBody().write(archive);
                        }
                    } finally {
                        exchange.close();
                    }
                });
        server.start();
    }

    int getPort() {
        return server.getAddress().getPort();
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }
}
