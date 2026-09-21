package cc.sbsj.mc.tracesdeath.compat;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ServerVersion {
    private final int major, minor, patch;

    public ServerVersion(int major, int minor, int patch) {
        this.major = major;
        this.minor = minor;
        this.patch = patch;
    }

    public static ServerVersion parse(String text) {
        Matcher match = Pattern.compile("^(\\d+)\\.(\\d+)(?:\\.(\\d+))?").matcher(text);
        if (!match.find()) throw new IllegalArgumentException("无法识别服务端版本: " + text);
        return new ServerVersion(
                Integer.parseInt(match.group(1)),
                Integer.parseInt(match.group(2)),
                match.group(3) == null ? 0 : Integer.parseInt(match.group(3)));
    }

    public boolean atLeast(int major, int minor, int patch) {
        if (this.major != major) return this.major > major;
        if (this.minor != minor) return this.minor > minor;
        return this.patch >= patch;
    }

    @Override
    public String toString() {
        return major + "." + minor + "." + patch;
    }
}
