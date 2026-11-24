package vendor.azenith;

interface IAZenithService {
    const int VERSION = 5;

    int getApiVersion();
    boolean setDisabled(boolean disabled);
    boolean isDisabled();
    boolean setConfig(in String key, in String value);
    boolean addGame(in String pkg);
    boolean removeGame(in String pkg);
    String getGames();
    String getState();
    String getConfig(in String key);
    String getGameConfigs();
    boolean setGameConfig(in String pkg, in String configJson);
    boolean removeGameConfig(in String pkg);
    boolean rescanGames();

    // ---- v5: appended, declaration order == transaction order ----
    // Start an allow-listed background task (bypass_list, bypass_check,
    // bypass_test <node>, bypass_restore, mali_check). Poll getTaskOutput.
    boolean startTask(in String name, in String arg);
    // Output of the last run of `name`; once finished the last line is
    // "##DONE <exit code>". Empty string when the task never ran.
    String getTaskOutput(in String name);
}
