package cc.sbsj.mc.tracesdeath.corpse;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

class CorpseItemsTest {
    static ItemStack item(Material type, int amount) {
        AtomicInteger count = new AtomicInteger(amount);
        ItemStack stack = mock(ItemStack.class);
        when(stack.getType()).thenReturn(type);
        when(stack.isEmpty()).thenAnswer(invocation -> count.get() <= 0 || type == Material.AIR);
        when(stack.getAmount()).thenAnswer(invocation -> count.get());
        doAnswer(
                        invocation -> {
                            count.set(invocation.getArgument(0));
                            return null;
                        })
                .when(stack)
                .setAmount(anyInt());
        when(stack.getMaxStackSize()).thenReturn(type == Material.DIAMOND_HELMET ? 1 : 64);
        when(stack.clone()).thenAnswer(invocation -> item(type, count.get()));
        when(stack.isSimilar(any()))
                .thenAnswer(
                        invocation -> {
                            ItemStack other = invocation.getArgument(0);
                            return other != null && other.getType() == type;
                        });
        return stack;
    }

    @Test
    void preservesSlotsAndConsumesDuplicateStacksOnlyOnce() {
        ItemStack[] inventory = new ItemStack[41];
        inventory[0] = item(Material.DIAMOND, 40);
        inventory[12] = item(Material.DIAMOND, 30);
        inventory[39] = item(Material.DIAMOND_HELMET, 1);
        inventory[40] = item(Material.TORCH, 8);
        var drops =
                List.of(
                        item(Material.DIAMOND, 50),
                        item(Material.DIAMOND_HELMET, 1),
                        item(Material.TORCH, 8));
        var captured = CorpseItems.capture(inventory, drops);
        assertEquals(Set.of(0, 12, 39, 40), captured.keySet());
        assertEquals(40, captured.get(0).getAmount());
        assertEquals(10, captured.get(12).getAmount());
        assertEquals(50, drops.getFirst().getAmount());
        assertEquals(30, inventory[12].getAmount());
    }

    @Test
    void doesNotRestoreItemsRemovedFromDeathDropsAndRetainsExtraDrops() {
        ItemStack[] inventory = new ItemStack[41];
        inventory[39] = item(Material.DIAMOND_HELMET, 1);
        var captured = CorpseItems.capture(inventory, List.of(item(Material.EMERALD, 4)));
        assertFalse(captured.containsKey(39));
        assertEquals(4, captured.get(41).getAmount());
    }

    @Test
    void partialSpacePreservesExactRemainderWithoutMutatingInput() {
        Map<Integer, ItemStack> inventory = new TreeMap<>();
        for (int i = 0; i < 36; i++) inventory.put(i, item(Material.STONE, 64));
        inventory.put(17, item(Material.DIAMOND, 61));
        var source = item(Material.DIAMOND, 12);
        var transfer = CorpseItems.plan(inventory, source, 64);
        assertEquals(9, transfer.remaining());
        assertEquals(64, transfer.after().get(17).getAmount());
        assertEquals(61, inventory.get(17).getAmount());
        assertEquals(12, source.getAmount());
    }

    @Test
    void mergesBeforeUsingEmptySlots() {
        var transfer =
                CorpseItems.plan(
                        Map.of(35, item(Material.DIAMOND, 60)), item(Material.DIAMOND, 10), 64);
        assertEquals(0, transfer.remaining());
        assertEquals(64, transfer.after().get(35).getAmount());
        assertEquals(6, transfer.after().get(0).getAmount());
    }

    @Test
    void fullInventoryCannotConsumeSource() {
        Map<Integer, ItemStack> inventory = new TreeMap<>();
        for (int i = 0; i < 36; i++) inventory.put(i, item(Material.STONE, 64));
        assertEquals(8, CorpseItems.plan(inventory, item(Material.DIAMOND, 8), 64).remaining());
    }

    @Test
    void respectsUnstackableItems() {
        var result = CorpseItems.plan(Map.of(), item(Material.DIAMOND_HELMET, 2), 64);
        assertEquals(0, result.remaining());
        assertEquals(1, result.after().get(0).getAmount());
        assertEquals(1, result.after().get(1).getAmount());
    }

    @Test
    void restoreReplacesOriginalEquipmentAndOnlyDropsDisplacedItems() {
        var before =
                Map.of(
                        0,
                        item(Material.STONE, 64),
                        39,
                        item(Material.GOLDEN_HELMET, 1),
                        40,
                        item(Material.TORCH, 8),
                        5,
                        item(Material.EMERALD, 5));
        var source =
                Map.of(
                        0,
                        item(Material.DIAMOND, 12),
                        39,
                        item(Material.DIAMOND_HELMET, 1),
                        40,
                        item(Material.SHIELD, 1));
        var plan = CorpseItems.planAll(before, source, false, 64);
        assertEquals(Material.DIAMOND, plan.after().get(0).getType());
        assertEquals(Material.DIAMOND_HELMET, plan.after().get(39).getType());
        assertEquals(Material.SHIELD, plan.after().get(40).getType());
        assertEquals(5, plan.after().get(5).getAmount());
        assertEquals(
                List.of(Material.STONE, Material.GOLDEN_HELMET, Material.TORCH),
                plan.drops().stream().map(ItemStack::getType).toList());
        assertEquals(73, plan.drops().stream().mapToInt(ItemStack::getAmount).sum());
        assertEquals(Material.STONE, before.get(0).getType());
    }

    @Test
    void inventoryModeDropsOnlyOverflowAndKeepsWornEquipment() {
        Map<Integer, ItemStack> before = new TreeMap<>();
        for (int i = 0; i < 36; i++) before.put(i, item(Material.STONE, 64));
        before.put(10, item(Material.DIAMOND, 61));
        before.put(39, item(Material.GOLDEN_HELMET, 1));
        var plan =
                CorpseItems.planAll(
                        before,
                        Map.of(0, item(Material.DIAMOND, 12), 39, item(Material.DIAMOND_HELMET, 1)),
                        true,
                        64);
        assertEquals(64, plan.after().get(10).getAmount());
        assertEquals(Material.GOLDEN_HELMET, plan.after().get(39).getType());
        assertEquals(9, plan.drops().get(0).getAmount());
        assertEquals(Material.DIAMOND_HELMET, plan.drops().get(1).getType());
    }

    @Test
    void extraDropsUseStorageAfterOriginalSlotsAreRestored() {
        var plan =
                CorpseItems.planAll(
                        Map.of(1, item(Material.COAL, 1)),
                        Map.of(0, item(Material.DIAMOND, 64), 41, item(Material.EMERALD, 12)),
                        false,
                        64);
        assertEquals(Material.DIAMOND, plan.after().get(0).getType());
        assertEquals(Material.COAL, plan.after().get(1).getType());
        assertEquals(Material.EMERALD, plan.after().get(2).getType());
        assertTrue(plan.drops().isEmpty());
    }

    @Test
    void guiMapsEveryPlayerSlotExactlyOnceAndOverflowDoesNotOverlap() {
        Set<Integer> slots = new HashSet<>();
        for (int gui = 0; gui < 54; gui++) {
            int source = CorpseItems.sourceSlot(0, gui);
            if (source >= 0) assertTrue(slots.add(source));
        }
        assertEquals(41, slots.size());
        for (int slot = 0; slot < 41; slot++) assertTrue(slots.contains(slot));
        assertEquals(39, CorpseItems.sourceSlot(0, 0));
        assertEquals(40, CorpseItems.sourceSlot(0, 4));
        assertEquals(0, CorpseItems.sourceSlot(0, 45));
        assertEquals(41, CorpseItems.sourceSlot(1, 18));
        assertEquals(77, CorpseItems.sourceSlot(2, 18));
        for (int slot = 5; slot < 18; slot++) {
            assertEquals(-1, CorpseItems.sourceSlot(0, slot));
            assertEquals(-1, CorpseItems.sourceSlot(1, slot));
        }
        assertEquals(39, CorpseItems.sourceSlot(1, 0));
        assertEquals(76, CorpseItems.sourceSlot(1, 53));
        assertEquals(2, CorpseItems.pages(Map.of(76, item(Material.DIAMOND, 1))));
        assertEquals(3, CorpseItems.pages(Map.of(77, item(Material.DIAMOND, 1))));
    }
}
