package me.foesio.foDrops.drop;

import java.util.ArrayList;
import java.util.List;

public class DropDefinition {
    private final String id;
    private String name;
    private DropEventType eventType;
    private boolean cancelVanillaDrops;
    private int priority;
    private boolean stopProcessing;
    private final DropConditions conditions;
    private final List<CustomDropEntry> customDrops;

    public DropDefinition(
        String id,
        String name,
        DropEventType eventType,
        boolean cancelVanillaDrops,
        int priority,
        boolean stopProcessing,
        DropConditions conditions,
        List<CustomDropEntry> customDrops
    ) {
        this.id = id;
        this.name = name;
        this.eventType = eventType;
        this.cancelVanillaDrops = cancelVanillaDrops;
        this.priority = priority;
        this.stopProcessing = stopProcessing;
        this.conditions = conditions == null ? new DropConditions() : conditions;
        this.customDrops = new ArrayList<>(customDrops);
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name != null && !name.isBlank()) {
            this.name = name;
        }
    }

    public DropEventType getEventType() {
        return eventType;
    }

    public void setEventType(DropEventType eventType) {
        this.eventType = eventType;
    }

    public boolean isCancelVanillaDrops() {
        return cancelVanillaDrops;
    }

    public void setCancelVanillaDrops(boolean cancelVanillaDrops) {
        this.cancelVanillaDrops = cancelVanillaDrops;
    }

    public int getPriority() {
        return priority;
    }

    public void setPriority(int priority) {
        this.priority = priority;
    }

    public boolean isStopProcessing() {
        return stopProcessing;
    }

    public void setStopProcessing(boolean stopProcessing) {
        this.stopProcessing = stopProcessing;
    }

    public DropConditions getConditions() {
        return conditions;
    }

    public List<CustomDropEntry> getCustomDrops() {
        return customDrops;
    }
}
