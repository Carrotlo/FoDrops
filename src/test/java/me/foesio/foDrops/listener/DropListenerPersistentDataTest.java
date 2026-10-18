package me.foesio.foDrops.listener;

import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.LinkedHashSet;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DropListenerPersistentDataTest {

    @Test
    void ignoresNonStringTagsWithoutReadingThemAsStrings() {
        NamespacedKey compoundKey = new NamespacedKey("other", "compound");
        NamespacedKey stringKey = new NamespacedKey("fodungeons", "mob-profile");
        LinkedHashSet<NamespacedKey> keys = new LinkedHashSet<>();
        keys.add(compoundKey);
        keys.add(stringKey);

        PersistentDataContainer container = (PersistentDataContainer) Proxy.newProxyInstance(
            PersistentDataContainer.class.getClassLoader(),
            new Class<?>[]{PersistentDataContainer.class},
            (proxy, method, arguments) -> switch (method.getName()) {
                case "getKeys" -> keys;
                case "has" -> stringKey.equals(arguments[0]) && PersistentDataType.STRING.equals(arguments[1]);
                case "get" -> {
                    if (compoundKey.equals(arguments[0])) {
                        throw new AssertionError("A compound tag was requested through the string adapter");
                    }
                    yield "egyptian_cat";
                }
                default -> throw new UnsupportedOperationException(method.getName());
            }
        );

        assertEquals(Map.of("fodungeons:mob-profile", "egyptian_cat"),
            DropListener.readStringPersistentData(container));
    }
}
