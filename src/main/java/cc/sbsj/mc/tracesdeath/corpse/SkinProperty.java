package cc.sbsj.mc.tracesdeath.corpse;

import java.util.LinkedHashMap;
import java.util.Map;

/** Portable profile property, stored without a dependency on a server profile implementation. */
public final class SkinProperty {
    public final String name, value, signature;

    public SkinProperty(String name, String value, String signature) {
        this.name = name;
        this.value = value;
        this.signature = signature;
    }

    public Map<String, Object> serialize() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("name", name);
        result.put("value", value);
        if (signature != null) result.put("signature", signature);
        return result;
    }
}
