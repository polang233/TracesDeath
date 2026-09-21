package cc.sbsj.mc.tracesdeath.storage;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.*;

class CorpseStoreTest {
    @TempDir Path directory;

    @Test
    void atomicallyReplacesExistingRecordAndLeavesNoTemporaryFile() throws Exception {
        Path file = directory.resolve("record.yml");
        CorpseStore.atomicWrite(file, "old");
        CorpseStore.atomicWrite(file, "new");
        assertEquals("new", Files.readString(file));
        assertFalse(Files.exists(directory.resolve("record.yml.tmp")));
    }

    @Test
    void failedReplacePreservesExistingTargetAndCleansTemporaryFile() throws Exception {
        Path target = directory.resolve("record.yml");
        Files.createDirectory(target);
        Files.writeString(target.resolve("keep"), "original");
        assertThrows(Exception.class, () -> CorpseStore.atomicWrite(target, "new"));
        assertEquals("original", Files.readString(target.resolve("keep")));
        assertFalse(Files.exists(directory.resolve("record.yml.tmp")));
    }

    @Test
    void savesExperienceOnlyCorpseAndPendingExperienceSnapshots() throws Exception {
        var id = java.util.UUID.randomUUID();
        var corpse =
                new cc.sbsj.mc.tracesdeath.corpse.Corpse(
                        id,
                        id,
                        "Player",
                        id,
                        0,
                        64,
                        0,
                        0,
                        0,
                        java.util.List.of(),
                        1,
                        java.util.Map.of());
        corpse.setExperience(36);
        var before = new cc.sbsj.mc.tracesdeath.experience.Experience.Snapshot(6, .5f, 123);
        corpse.begin(
                new cc.sbsj.mc.tracesdeath.corpse.Corpse.PendingClaim(
                        id,
                        41,
                        java.util.Map.of(),
                        java.util.Map.of(),
                        java.util.Map.of(),
                        java.util.List.of(),
                        false,
                        before,
                        before.add(36),
                        36));
        var store = new CorpseStore(directory);
        store.save(corpse);
        var loaded = store.load().getFirst();
        assertEquals(36, loaded.experience());
        assertEquals(36, loaded.pending().experienceTaken());
        assertEquals(.5f, loaded.pending().experienceBefore().progress);
        assertEquals(123, loaded.pending().experienceBefore().total);
        loaded.finish(true);
        store.save(loaded);
        assertTrue(store.load().isEmpty());
    }

    @Test
    void corruptRecordStopsLoadingInsteadOfDiscardingItems() throws Exception {
        CorpseStore store = new CorpseStore(directory);
        Files.writeString(directory.resolve("broken.yml"), "schema: 99\n");
        assertThrows(Exception.class, store::load);
        assertTrue(Files.exists(directory.resolve("broken.yml")));
    }
}
