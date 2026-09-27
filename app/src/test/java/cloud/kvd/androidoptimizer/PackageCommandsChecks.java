package cloud.kvd.androidoptimizer;

import java.util.*;

/** Runs on a plain JDK as well as through the Android test suite. */
public final class PackageCommandsChecks {
    private static final String PKG = "com.transsion.statisticalsales";
    public static void main(String[] args) throws Exception {
        FakeRunner shell = new FakeRunner();
        PackageCommands executor = new PackageCommands(shell);
        check(executor.change(PKG, 10, false).success, "disable must succeed after verification");
        check(shell.disabled, "disable must change package state");
        check(shell.commands.contains(Arrays.asList("/system/bin/pm", "disable-user", "--user", "10", PKG)), "must target explicit user");
        check(executor.change(PKG, 10, true).success && !shell.disabled, "enable must restore package");
        int count = shell.commands.size();
        check(!executor.change("com.foo;reboot", 0, false).success, "reject shell injection");
        check(!executor.change(PKG, -1, false).success, "reject all-users sentinel");
        check(!executor.change("com.android.systemui", 0, false).success, "protect system UI");
        check(!executor.change("moe.shizuku.privileged.api", 0, false).success, "protect access provider");
        check(!executor.change("com.hoffnung", 0, false).success, "protect known boot component");
        check(shell.commands.size() == count, "invalid/protected requests must not execute anything");
        shell.deny = true;
        check(!executor.change(PKG, 0, false).success && !shell.disabled, "denial must not report success");
        shell.deny = false; shell.ignoreChange = true;
        check(!executor.change(PKG, 0, false).success, "zero exit without actual change must fail");
        shell.ignoreChange = false; shell.installed = false;
        count = shell.commands.size();
        check(!executor.change(PKG, 0, false).success, "missing exact package must fail");
        check(shell.commands.subList(count, shell.commands.size()).stream().noneMatch(c -> c.contains("disable-user")), "never mutate missing package");
        shell.installed = true; shell.throwOnRead = true;
        check(!executor.change(PKG, 0, false).success, "transport error must be explicit");
        System.out.println("Package command safety checks passed");
    }
    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static final class FakeRunner implements PackageCommands.Runner {
        boolean disabled, deny, ignoreChange, throwOnRead;
        boolean installed = true;
        final List<List<String>> commands = new ArrayList<>();
        public PackageCommands.Output run(List<String> command) throws Exception {
            commands.add(new ArrayList<>(command));
            if (command.contains("list")) {
                if (throwOnRead) throw new java.io.IOException("disconnected");
                // A substring match must not count as the target package.
                String value = "package:" + PKG + ".other\n";
                if (installed && (!command.contains("-d") || disabled)) value += "package:" + PKG + "\n";
                return new PackageCommands.Output(0, value);
            }
            if (deny) return new PackageCommands.Output(1, "SecurityException: not permitted");
            if (!ignoreChange) disabled = command.contains("disable-user");
            return new PackageCommands.Output(0, "Package changed");
        }
    }
}
