package cc.sbsj.mc.tracesDeath.corpse;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CorpseStoreTest {
    @TempDir Path directory;

    @Test void atomicallyReplacesExistingRecordAndLeavesNoTemporaryFile() throws Exception {
        Path file = directory.resolve("record.yml");
        CorpseStore.atomicWrite(file, "old");
        CorpseStore.atomicWrite(file, "new");
        assertEquals("new", Files.readString(file));
        assertFalse(Files.exists(directory.resolve("record.yml.tmp")));
    }

    @Test void failedReplacePreservesExistingTargetAndCleansTemporaryFile() throws Exception {
        Path target = directory.resolve("record.yml");
        Files.createDirectory(target);
        Files.writeString(target.resolve("keep"), "original");
        assertThrows(Exception.class, () -> CorpseStore.atomicWrite(target, "new"));
        assertEquals("original", Files.readString(target.resolve("keep")));
        assertFalse(Files.exists(directory.resolve("record.yml.tmp")));
    }

    @Test void corruptRecordStopsLoadingInsteadOfDiscardingItems() throws Exception {
        CorpseStore store = new CorpseStore(directory);
        Files.writeString(directory.resolve("broken.yml"), "schema: 99\n");
        assertThrows(Exception.class, store::load);
        assertTrue(Files.exists(directory.resolve("broken.yml")));
    }
}
