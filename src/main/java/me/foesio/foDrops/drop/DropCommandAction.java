package me.foesio.foDrops.drop;

public class DropCommandAction {
    private DropCommandSenderType senderType;
    private String command;
    private String normalizedCommand;
    private boolean hasPlaceholders;

    public DropCommandAction(DropCommandSenderType senderType, String command) {
        setSenderType(senderType);
        setCommand(command);
    }

    public DropCommandSenderType getSenderType() {
        return senderType;
    }

    public void setSenderType(DropCommandSenderType senderType) {
        this.senderType = senderType == null ? DropCommandSenderType.CONSOLE : senderType;
    }

    public String getCommand() {
        return command;
    }

    public String getNormalizedCommand() {
        return normalizedCommand;
    }

    public boolean hasPlaceholders() {
        return hasPlaceholders;
    }

    public void setCommand(String command) {
        this.command = command == null ? "" : command.trim();
        this.normalizedCommand = normalizeCommand(this.command);
        this.hasPlaceholders = normalizedCommand.indexOf('{') >= 0 || normalizedCommand.indexOf('%') >= 0;
    }

    private String normalizeCommand(String command) {
        String normalized = command.trim();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1).trim();
        }
        return normalized;
    }
}
