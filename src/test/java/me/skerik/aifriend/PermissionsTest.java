package me.skerik.aifriend;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

/** Every permission the code checks must be declared in paper-plugin.yml (otherwise it defaults to op-only). */
class PermissionsTest {

    @Test
    void everyCheckedPermissionIsDeclared() throws Exception {
        YamlConfiguration y = new YamlConfiguration();
        try (InputStream in = PermissionsTest.class.getResourceAsStream("/paper-plugin.yml")) {
            y.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        Set<String> declared = new TreeSet<>();
        for (String k : y.getConfigurationSection("permissions").getKeys(true)) {
            if (k.endsWith(".description")) declared.add(k.substring(0, k.length() - ".description".length()));
        }
        Set<String> used = new TreeSet<>();
        Pattern p = Pattern.compile("hasPermission\\(\"([^\"]+)\"");
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path f : files.filter(x -> x.toString().endsWith(".java")).toList()) {
                Matcher m = p.matcher(Files.readString(f));
                while (m.find()) used.add(m.group(1));
            }
        }
        assertFalse(used.isEmpty());
        for (String perm : used) assertTrue(declared.contains(perm), perm + " is checked but not declared");
        // permissions passed as strings to the notifier
        assertTrue(declared.contains("aifriend.cost"));
        assertTrue(declared.size() >= 9);
    }
}
