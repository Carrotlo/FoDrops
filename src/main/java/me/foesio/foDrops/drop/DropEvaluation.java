package me.foesio.foDrops.drop;

import java.util.ArrayList;
import java.util.List;

public class DropEvaluation {
    private final List<DropDefinition> matchedDefinitions = new ArrayList<>();
    private boolean cancelVanillaDrops;

    public List<DropDefinition> getMatchedDefinitions() {
        return matchedDefinitions;
    }

    public boolean isCancelVanillaDrops() {
        return cancelVanillaDrops;
    }

    public void setCancelVanillaDrops(boolean cancelVanillaDrops) {
        this.cancelVanillaDrops = cancelVanillaDrops;
    }
}
