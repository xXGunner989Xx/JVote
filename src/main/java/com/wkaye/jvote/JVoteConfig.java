package com.wkaye.jvote;

import org.bukkit.util.config.Configuration;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

public class JVoteConfig extends Configuration {
    private static JVoteConfig singleton;

    public JVoteConfig(JVote plugin) {
        super(new File(plugin.getDataFolder(), "core.yml"));
        this.reload();
    }

    public static JVoteConfig getInstance() {
        if (JVoteConfig.singleton == null) {
            throw new RuntimeException("An instance of JVote hasn't been passed in to config yet");
        }
        return JVoteConfig.singleton;
    }

    public static JVoteConfig getInstance(JVote plugin) {
        if (JVoteConfig.singleton == null) {
            JVoteConfig.singleton = new JVoteConfig(plugin);
        }
        return JVoteConfig.singleton;
    }

    private void write() {
        generateConfigOption("config-version", 1);
        generateConfigOption("settings.toggle-timer", true);
        generateConfigOption("settings.timer-length", 60);
        generateConfigOption("settings.reminder-frequency", new ArrayList<>(Arrays.asList(60, 30, 20, 10, 5)));
        generateConfigOption("settings.debug-level", 0);
    }

    private void reload() {
        this.load();
        this.write();
        this.save();
    }

    public void generateConfigOption(String key, Object defaultValue) {
        if (this.getProperty(key) == null) {
            this.setProperty(key, defaultValue);
        }
        final Object value = this.getProperty(key);
        this.removeProperty(key);
        this.setProperty(key, value);
    }

    public Object getConfigOption(String key) {
        return this.getProperty(key);
    }

    public String getConfigString(String key) {
        return String.valueOf(getConfigOption(key));
    }

    public Integer getConfigInteger(String key) {
        return Integer.valueOf(getConfigString(key));
    }

    public Long getConfigLong(String key) {
        return Long.valueOf(getConfigString(key));
    }

    public Double getConfigDouble(String key) {
        return Double.valueOf(getConfigString(key));
    }

    public Boolean getConfigBoolean(String key) {
        return Boolean.valueOf(getConfigString(key));
    }
}
