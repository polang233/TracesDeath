package cc.sbsj.mc.tracesdeath.compat;

import cc.sbsj.mc.tracesdeath.resourcepack.CustomTextureSettings;

public final class ServerAdapterFactory {
    private ServerAdapterFactory() {}

    public static ServerAdapter load(ServerVersion version, CustomTextureSettings assets) {
        if (version.atLeast(1, 19, 4) && present("com.destroystokyo.paper.profile.PlayerProfile")) {
            try {
                return (ServerAdapter)
                        Class.forName("cc.sbsj.mc.tracesdeath.compat.PaperServerAdapter")
                                .getConstructor(ServerVersion.class, CustomTextureSettings.class)
                                .newInstance(version, assets);
            } catch (ReflectiveOperationException exception) {
                throw new IllegalStateException("加载现代服务端适配器失败", exception);
            }
        }
        return new BukkitServerAdapter();
    }

    private static boolean present(String name) {
        try {
            Class.forName(name);
            return true;
        } catch (ClassNotFoundException exception) {
            return false;
        }
    }
}
