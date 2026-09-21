package cc.sbsj.mc.tracesdeath.resourcepack;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.net.*;

class ResourcePackHttpServerTest {
    @Test
    void servesOnlyArchiveAndReleasesPortOnClose() throws Exception {
        byte[] bytes = {80, 75, 3, 4, 1, 2, 3};
        int port;
        try (var server =
                new ResourcePackHttpServer("127.0.0.1", 0, java.util.Map.of("/pack.zip", bytes))) {
            port = server.getPort();
            var get =
                    (HttpURLConnection)
                            new URL("http://127.0.0.1:" + port + "/pack.zip").openConnection();
            assertEquals(200, get.getResponseCode());
            try (var input = get.getInputStream()) {
                assertArrayEquals(bytes, input.readAllBytes());
            }
            var head =
                    (HttpURLConnection)
                            new URL("http://127.0.0.1:" + port + "/pack.zip").openConnection();
            head.setRequestMethod("HEAD");
            assertEquals(bytes.length, head.getContentLength());
            assertEquals(200, head.getResponseCode());
            head.disconnect();
            var forbidden =
                    (HttpURLConnection)
                            new URL("http://127.0.0.1:" + port + "/pack.zip/../config.yml")
                                    .openConnection();
            assertEquals(404, forbidden.getResponseCode());
            forbidden.disconnect();
        }
        try (var socket = new java.net.ServerSocket(port, 1, InetAddress.getByName("127.0.0.1"))) {
            assertTrue(socket.isBound());
        }
    }
}
