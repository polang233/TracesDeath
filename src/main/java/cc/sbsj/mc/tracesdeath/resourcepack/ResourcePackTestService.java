package cc.sbsj.mc.tracesdeath.resourcepack;

import cc.sbsj.mc.tracesdeath.compat.ServerVersion;

import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.net.URI;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Exports the bundled resource pack. Starts download hosting only on an explicit test command. */
public final class ResourcePackTestService implements AutoCloseable {
    private final JavaPlugin plugin;
    private final CustomTextureSettings settings;
    private final ServerVersion version;
    private final Map<String, byte[]> archives = new LinkedHashMap<>();
    private ResourcePackHttpServer server;

    public ResourcePackTestService(
            JavaPlugin plugin, CustomTextureSettings settings, ServerVersion version) {
        this.plugin = plugin;
        this.settings = settings;
        this.version = version;
    }

    public void exportPacks() throws Exception {
        Path directory = plugin.getDataFolder().toPath().resolve("resource-packs");
        Files.createDirectories(directory);
        String filename = "tracesdeath.zip";
        byte[] bytes;
        try (InputStream input = plugin.getResource("resource-packs/" + filename)) {
            if (input == null) throw new IOException("内置资源包缺失: " + filename);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            bytes = output.toByteArray();
        }
        archives.put("/" + filename, bytes);
        Path file = directory.resolve(plugin.getDescription().getVersion() + "-" + filename);
        Files.write(file, bytes);
        StringBuilder sha1 = new StringBuilder();
        for (byte value : digest(bytes)) sha1.append(String.format("%02x", value & 255));
        Files.write(
                directory.resolve(file.getFileName() + ".sha1"),
                (sha1 + "\n").getBytes(java.nio.charset.StandardCharsets.US_ASCII));
    }

    public void send(Player player) throws Exception {
        if (!version.atLeast(1, 20, 0))
            throw new IllegalArgumentException("内置材质包测试需要 Minecraft 1.20+");
        if (archives.isEmpty()) exportPacks();
        String baseUrl = settings.getPublicUrl().replaceAll("/+$", "");
        URI uri = new URI(baseUrl);
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null
                || uri.getUserInfo() != null
                || uri.getQuery() != null
                || uri.getFragment() != null
                || !uri.toASCIIString().equals(baseUrl))
            throw new IllegalArgumentException("请在 tombstone.yml 设置有效的 test-server.public-url");
        if (settings.getPort() < 1 || settings.getPort() > 65535)
            throw new IllegalArgumentException("tombstone.yml 的 test-server.port 必须介于 1 和 65535");
        String filename = "tracesdeath.zip";
        byte[] archive = archives.get("/" + filename);
        if (archive == null) throw new IllegalStateException("内置材质包未成功导出，请查看启动日志");
        if (server == null)
            server =
                    new ResourcePackHttpServer(
                            settings.getBindAddress(), settings.getPort(), archives);
        player.setResourcePack(baseUrl + "/" + filename, digest(archive));
    }

    private static byte[] digest(byte[] bytes) throws Exception {
        return MessageDigest.getInstance("SHA-1").digest(bytes);
    }

    @Override
    public void close() {
        if (server != null) {
            server.close();
            server = null;
        }
    }
}
