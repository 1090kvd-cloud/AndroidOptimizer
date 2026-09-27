package cloud.kvd.androidoptimizer;

import java.util.*;
import java.util.regex.Pattern;

/** Narrow command boundary: no shell, arbitrary commands, deletion, or implicit user. */
public final class PackageCommands {
    public interface Runner { Output run(List<String> command) throws Exception; }
    public static final class Output {
        public final int code; public final String text;
        public Output(int code, String text) { this.code = code; this.text = text; }
    }
    public static final class Result {
        public final boolean success; public final String message;
        public Result(boolean success, String message) { this.success = success; this.message = message; }
    }
    private final Runner runner;
    private static final Pattern PACKAGE = Pattern.compile("[A-Za-z][A-Za-z0-9_]*(\\.[A-Za-z][A-Za-z0-9_]*)+");
    private static final String[] PROTECTED = {
        "android", "com.android.systemui", "com.android.settings", "com.android.phone",
        "com.android.providers", "com.android.permissioncontroller", "com.google.android.permissioncontroller",
        "com.android.packageinstaller", "com.google.android.packageinstaller", "com.android.shell",
        "com.android.networkstack", "com.google.android.networkstack", "com.android.bluetooth",
        "com.android.nfc", "com.android.se", "com.android.server.telecom", "com.android.keychain",
        "com.google.android.gms", "com.google.android.gsf", "com.android.webview", "com.google.android.webview",
        "com.android.launcher", "com.android.launcher3", "com.transsion.XOSLauncher", "com.transsion.hilauncher",
        "cloud.kvd.androidoptimizer", "moe.shizuku.privileged.api", "com.hoffnung"
    };
    public PackageCommands(Runner runner) { this.runner = runner; }
    public static boolean isProtected(String pkg) {
        for (String prefix : PROTECTED) if (pkg.equals(prefix) || pkg.startsWith(prefix + ".")) return true;
        return false;
    }
    public Result change(String pkg, int user, boolean enabled) {
        if (pkg == null || !PACKAGE.matcher(pkg).matches() || user < 0)
            return new Result(false, "Некорректный пакет или пользователь.");
        if (!enabled && isProtected(pkg)) return new Result(false, "Отключение защищённого компонента запрещено.");
        try {
            if (!containsPackage(read(user, pkg, false), pkg))
                return new Result(false, "Пакет не установлен для этого пользователя.");
            Output changed = runner.run(Arrays.asList("/system/bin/pm", enabled ? "enable" : "disable-user", "--user", String.valueOf(user), pkg));
            if (changed.code != 0)
                return new Result(false, "Android отклонил изменение: " + shortText(changed.text));
            boolean disabled = containsPackage(read(user, pkg, true), pkg);
            if (!containsPackage(read(user, pkg, false), pkg) || disabled == enabled)
                return new Result(false, "Изменение не подтверждено. Обновите список и проверьте состояние.");
            return new Result(true, enabled ? "Приложение включено." : "Приложение отключено для текущего пользователя.");
        } catch (Exception error) {
            return new Result(false, "Не удалось подтвердить результат: " + shortText(error.getMessage()) + ". Обновите список.");
        }
    }
    private String read(int user, String pkg, boolean disabledOnly) throws Exception {
        List<String> command = new ArrayList<>(Arrays.asList("/system/bin/pm", "list", "packages", "--user", String.valueOf(user)));
        if (disabledOnly) command.add("-d");
        command.add(pkg);
        Output output = runner.run(command);
        if (output.code != 0) throw new java.io.IOException(shortText(output.text));
        return output.text;
    }
    private static boolean containsPackage(String output, String pkg) {
        return Arrays.stream(output.split("\\r?\\n")).anyMatch(line -> line.trim().equals("package:" + pkg));
    }
    private static String shortText(String value) {
        if (value == null || value.isEmpty()) return "нет ответа";
        return value.substring(0, Math.min(value.length(), 350)).trim();
    }
}
