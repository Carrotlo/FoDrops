package me.foesio.foDrops.drop;

import java.util.ArrayList;
import java.util.List;

public class DropConditions {
    private final List<String> worlds = new ArrayList<>();
    private final List<String> disabledWorlds = new ArrayList<>();
    private final List<String> biomes = new ArrayList<>();
    private final List<String> targets = new ArrayList<>();
    private String permission = "";
    private final List<String> tools = new ArrayList<>();
    private SilkTouchMode silkTouchMode = SilkTouchMode.IGNORE;
    private int fortuneMin = 0;
    private long cooldownMs = 0L;
    private Integer timeMin;
    private Integer timeMax;
    private final List<DropWeather> weather = new ArrayList<>();

    public List<String> getWorlds() {
        return worlds;
    }

    public List<String> getDisabledWorlds() {
        return disabledWorlds;
    }

    public List<String> getBiomes() {
        return biomes;
    }

    public List<String> getTargets() {
        return targets;
    }

    public String getPermission() {
        return permission;
    }

    public void setPermission(String permission) {
        this.permission = permission == null ? "" : permission.trim();
    }

    public List<String> getTools() {
        return tools;
    }

    public SilkTouchMode getSilkTouchMode() {
        return silkTouchMode;
    }

    public void setSilkTouchMode(SilkTouchMode silkTouchMode) {
        this.silkTouchMode = silkTouchMode == null ? SilkTouchMode.IGNORE : silkTouchMode;
    }

    public int getFortuneMin() {
        return fortuneMin;
    }

    public void setFortuneMin(int fortuneMin) {
        this.fortuneMin = Math.max(0, fortuneMin);
    }

    public long getCooldownMs() {
        return cooldownMs;
    }

    public void setCooldownMs(long cooldownMs) {
        this.cooldownMs = Math.max(0L, cooldownMs);
    }

    public Integer getTimeMin() {
        return timeMin;
    }

    public Integer getTimeMax() {
        return timeMax;
    }

    public void setTimeRange(Integer min, Integer max) {
        if (min == null || max == null) {
            this.timeMin = null;
            this.timeMax = null;
            return;
        }
        int fixedMin = normalizeTime(min);
        int fixedMax = normalizeTime(max);
        this.timeMin = fixedMin;
        this.timeMax = fixedMax;
    }

    public List<DropWeather> getWeather() {
        return weather;
    }

    public boolean hasAnyRestrictions() {
        return !worlds.isEmpty()
            || !disabledWorlds.isEmpty()
            || !biomes.isEmpty()
            || !targets.isEmpty()
            || !permission.isBlank()
            || !tools.isEmpty()
            || silkTouchMode != SilkTouchMode.IGNORE
            || fortuneMin > 0
            || cooldownMs > 0L
            || timeMin != null
            || timeMax != null
            || !weather.isEmpty();
    }

    private int normalizeTime(int value) {
        int wrapped = value % 24000;
        if (wrapped < 0) {
            wrapped += 24000;
        }
        return wrapped;
    }
}
