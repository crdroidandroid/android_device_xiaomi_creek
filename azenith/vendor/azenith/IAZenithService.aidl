package vendor.azenith;

interface IAZenithService {
    const int VERSION = 4;

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
}
