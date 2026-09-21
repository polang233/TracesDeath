package cc.sbsj.mc.tracesdeath.storage;

import cc.sbsj.mc.tracesdeath.corpse.Corpse;
import cc.sbsj.mc.tracesdeath.corpse.CorpseItems;
import cc.sbsj.mc.tracesdeath.corpse.SkinProperty;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.inventory.ItemStack;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Each record includes its pending transfer; a failed write never publishes new memory state. */
public final class CorpseStore {
    private final Path directory;

    public CorpseStore(Path directory) throws IOException {
        this.directory = directory;
        Files.createDirectories(directory);
    }

    public List<Corpse> load() throws Exception {
        List<Corpse> result = new ArrayList<>();
        try (Stream<Path> files = Files.list(directory)) {
            for (Path file :
                    files.filter(p -> p.toString().endsWith(".yml"))
                            .sorted()
                            .collect(Collectors.toList())) {
                YamlConfiguration yaml = new YamlConfiguration();
                yaml.load(
                        file.toFile()); // Do not silently turn a damaged file into an empty record.
                if (yaml.getInt("schema") < 1 || yaml.getInt("schema") > 3)
                    throw new IOException("Unsupported schema: " + file);
                UUID id = UUID.fromString(required(yaml, "id"));
                if (!file.getFileName().toString().equals(id + ".yml"))
                    throw new IOException("ID mismatch: " + file);
                if (yaml.getBoolean("completed")) continue;
                List<SkinProperty> skin = readSkin(yaml);
                double x = yaml.getDouble("x"), y = yaml.getDouble("y"), z = yaml.getDouble("z");
                float yaw = (float) yaml.getDouble("yaw");
                int heldSlot = yaml.getInt("held-slot");
                if (!Double.isFinite(x)
                        || !Double.isFinite(y)
                        || !Double.isFinite(z)
                        || !Float.isFinite(yaw)
                        || heldSlot < 0
                        || heldSlot > 8) throw new IOException("Invalid position/slot: " + file);
                Corpse corpse =
                        new Corpse(
                                id,
                                UUID.fromString(required(yaml, "owner")),
                                required(yaml, "name"),
                                UUID.fromString(required(yaml, "world")),
                                x,
                                y,
                                z,
                                yaw,
                                heldSlot,
                                skin,
                                Math.max(0, yaml.getLong("death-time", 0)),
                                readItems(yaml, "items", Integer.MAX_VALUE));
                if (yaml.contains("pending")) {
                    int inventorySize = yaml.getInt("pending.inventory-size", 36);
                    corpse.begin(
                            new Corpse.PendingClaim(
                                    UUID.fromString(required(yaml, "pending.player")),
                                    inventorySize,
                                    readItems(yaml, "pending.before", inventorySize - 1),
                                    readItems(yaml, "pending.after", inventorySize - 1),
                                    readItems(yaml, "pending.remaining", Integer.MAX_VALUE),
                                    yaml.contains("pending.drops")
                                            ? new ArrayList<>(
                                                    readItems(
                                                                    yaml,
                                                                    "pending.drops",
                                                                    Integer.MAX_VALUE)
                                                            .values())
                                            : Collections.emptyList(),
                                    yaml.getBoolean("pending.drops-started", false)));
                }
                result.add(corpse);
            }
        }
        return result;
    }

    public void save(Corpse corpse) throws IOException {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("schema", 3);
        yaml.set("id", corpse.id.toString());
        // A durable empty marker prevents failed deletion from resurrecting old inventory.
        yaml.set("completed", corpse.empty() && corpse.pending() == null);
        if (!corpse.empty() || corpse.pending() != null) {
            yaml.set("owner", corpse.owner.toString());
            yaml.set("name", corpse.name);
            yaml.set("world", corpse.world.toString());
            yaml.set("x", corpse.x);
            yaml.set("y", corpse.y);
            yaml.set("z", corpse.z);
            yaml.set("yaw", corpse.yaw);
            yaml.set("held-slot", corpse.heldSlot);
            yaml.set(
                    "skin",
                    corpse.skin.stream().map(SkinProperty::serialize).collect(Collectors.toList()));
            yaml.set("death-time", corpse.deathTime);
            writeItems(yaml, "items", corpse.items());
            Corpse.PendingClaim pending = corpse.pending();
            if (pending != null) {
                yaml.set("pending.player", pending.player().toString());
                yaml.set("pending.inventory-size", pending.inventorySize());
                yaml.set("pending.drops-started", pending.dropsStarted());
                writeItems(
                        yaml,
                        "pending.drops",
                        CorpseItems.snapshot(pending.drops().toArray(new ItemStack[0])));
                writeItems(yaml, "pending.before", pending.before());
                writeItems(yaml, "pending.after", pending.after());
                writeItems(yaml, "pending.remaining", pending.remaining());
            }
        }
        atomicWrite(directory.resolve(corpse.id + ".yml"), yaml.saveToString());
    }

    static void atomicWrite(Path target, String contents) throws IOException {
        Path temp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            try (FileChannel channel =
                    FileChannel.open(
                            temp,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING,
                            StandardOpenOption.WRITE)) {
                ByteBuffer bytes = StandardCharsets.UTF_8.encode(contents);
                while (bytes.hasRemaining()) channel.write(bytes);
                channel.force(true);
            }
            // Refuse unsafe fallback when this filesystem cannot atomically replace a record.
            Files.move(
                    temp,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static List<SkinProperty> readSkin(YamlConfiguration yaml) throws IOException {
        Object value;
        if (yaml.getInt("schema") >= 3) {
            value = yaml.get("skin");
        } else {
            Object profile = yaml.get("profile");
            Map<?, ?> fields;
            if (profile instanceof ConfigurationSerializable)
                fields = ((ConfigurationSerializable) profile).serialize();
            else if (profile instanceof ConfigurationSection)
                fields = ((ConfigurationSection) profile).getValues(false);
            else if (profile instanceof Map) fields = (Map<?, ?>) profile;
            else throw new IOException("Missing profile");
            value =
                    fields.containsKey("properties")
                            ? fields.get("properties")
                            : Collections.emptyList();
        }
        if (!(value instanceof List)) throw new IOException("Invalid skin properties");
        List<SkinProperty> result = new ArrayList<>();
        for (Object entry : (List<?>) value) {
            if (!(entry instanceof Map)) throw new IOException("Invalid skin property");
            Map<?, ?> property = (Map<?, ?>) entry;
            if (!(property.get("name") instanceof String)
                    || !(property.get("value") instanceof String)) {
                throw new IOException("Invalid skin property fields");
            }
            Object signature = property.get("signature");
            if (signature != null && !(signature instanceof String))
                throw new IOException("Invalid skin signature");
            result.add(
                    new SkinProperty(
                            (String) property.get("name"),
                            (String) property.get("value"),
                            (String) signature));
        }
        return result;
    }

    private static String required(YamlConfiguration yaml, String key) throws IOException {
        String value = yaml.getString(key);
        if (value == null || value.trim().isEmpty()) throw new IOException("Missing field: " + key);
        return value;
    }

    private static void writeItems(
            YamlConfiguration yaml, String path, Map<Integer, ItemStack> items) {
        yaml.createSection(path);
        items.forEach((slot, item) -> yaml.set(path + "." + slot, item));
    }

    private static Map<Integer, ItemStack> readItems(
            YamlConfiguration yaml, String path, int maxSlot) throws IOException {
        ConfigurationSection section = yaml.getConfigurationSection(path);
        if (section == null) throw new IOException("Missing inventory: " + path);
        Map<Integer, ItemStack> items = new TreeMap<>();
        for (String key : section.getKeys(false)) {
            int slot = Integer.parseInt(key);
            Object raw = section.get(key);
            if (slot < 0
                    || slot > maxSlot
                    || !(raw instanceof ItemStack)
                    || CorpseItems.empty((ItemStack) raw)) {
                throw new IOException("Invalid item: " + path + "." + key);
            }
            items.put(slot, ((ItemStack) raw).clone());
        }
        return items;
    }
}
