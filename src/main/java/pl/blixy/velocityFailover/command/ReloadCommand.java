package pl.blixy.velocityFailover.command;

import com.velocitypowered.api.command.SimpleCommand;
import net.kyori.adventure.text.minimessage.MiniMessage;
import pl.blixy.velocityFailover.VelocityFailover;

public class ReloadCommand implements SimpleCommand {

    private final VelocityFailover plugin;

    public ReloadCommand(VelocityFailover plugin) {
        this.plugin = plugin;
    }

    @Override
    public void execute(Invocation invocation) {
        try {
            plugin.reload();
            invocation.source().sendMessage(MiniMessage.miniMessage().deserialize("<green>[Failover] Configuration reloaded successfully."));
        } catch (Exception e) {
            invocation.source().sendMessage(MiniMessage.miniMessage().deserialize("<red>[Failover] Failed to reload configuration: " + e.getMessage()));
        }
    }

    @Override
    public boolean hasPermission(Invocation invocation) {
        return invocation.source().hasPermission("velocityfailover.reload");
    }
}
