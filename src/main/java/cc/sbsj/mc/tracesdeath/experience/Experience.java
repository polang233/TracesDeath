package cc.sbsj.mc.tracesdeath.experience;

import org.bukkit.entity.Player;

/** Experience points represented by the player's current level and progress bar. */
public final class Experience {
    private Experience() {}

    public static long atLevel(int level) {
        double n = level;
        return (long)
                (level <= 16
                        ? n * n + 6 * n
                        : level <= 31
                                ? 2.5 * n * n - 40.5 * n + 360
                                : 4.5 * n * n - 162.5 * n + 2220);
    }

    public static long toNextLevel(int level) {
        return level <= 15 ? 2L * level + 7 : level <= 30 ? 5L * level - 38 : 9L * level - 158;
    }

    public static int points(int level, float progress) {
        return (int)
                Math.min(
                        Integer.MAX_VALUE,
                        (double) atLevel(level)
                                + Math.round((double) progress * toNextLevel(level)));
    }

    public static int level(int points) {
        int low = 0, high = 30000;
        while (low < high) {
            int mid = (low + high + 1) / 2;
            if (atLevel(mid) <= points) low = mid;
            else high = mid - 1;
        }
        return low;
    }

    /**
     * Keep the exact before/after state for crash recovery, including Bukkit's historical total.
     */
    public static final class Snapshot {
        public final int level, total;
        public final float progress;

        public Snapshot(int level, float progress, int total) {
            if (level < 0
                    || !Float.isFinite(progress)
                    || progress < 0
                    || progress >= 1
                    || total < 0) throw new IllegalArgumentException("Invalid experience snapshot");
            this.level = level;
            this.progress = progress;
            this.total = total;
        }

        public static Snapshot capture(Player player) {
            return new Snapshot(player.getLevel(), player.getExp(), player.getTotalExperience());
        }

        public Snapshot add(int amount) {
            int points = Math.addExact(points(level, progress), amount);
            int nextLevel = level(points);
            float nextProgress = (float) (points - atLevel(nextLevel)) / toNextLevel(nextLevel);
            return new Snapshot(
                    nextLevel,
                    nextProgress,
                    (int) Math.min(Integer.MAX_VALUE, (long) total + amount));
        }

        public void apply(Player player) {
            player.setLevel(level);
            player.setExp(progress);
            player.setTotalExperience(total);
        }

        public boolean matches(Player player) {
            return level == player.getLevel()
                    && Float.compare(progress, player.getExp()) == 0
                    && total == player.getTotalExperience();
        }
    }
}
