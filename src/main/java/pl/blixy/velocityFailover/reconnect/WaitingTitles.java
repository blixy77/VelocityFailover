package pl.blixy.velocityFailover.reconnect;

import com.velocitypowered.api.proxy.ProxyServer;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.title.Title;
import pl.blixy.velocityFailover.config.FailoverConfig;
import pl.blixy.velocityFailover.server.ServerState;
import pl.blixy.velocityFailover.server.ServerStates;

import java.util.List;
import java.util.UUID;

/** Keeps an animated title visible while players wait, switching frames when their server starts recovering. */
public final class WaitingTitles implements Runnable {

    private final ProxyServer proxy;
    private final FailoverConfig config;
    private final WaitingPlayers waiting;
    private final ServerStates states;
    private int waitingFrame;
    private int connectingFrame;

    public WaitingTitles(ProxyServer proxy, FailoverConfig config, WaitingPlayers waiting, ServerStates states) {
        this.proxy = proxy;
        this.config = config;
        this.waiting = waiting;
        this.states = states;
    }

    @Override
    public void run() {
        List<Title> waitingFrames = config.titleAnimation().waitingFrames();
        List<Title> connectingFrames = config.titleAnimation().connectingFrames();

        for (UUID uuid : waiting.all()) {
            proxy.getPlayer(uuid).filter(config::isLimbo).ifPresent(player -> waiting.serverOf(uuid).ifPresent(server -> {
                boolean connecting = states.state(server) == ServerState.RECOVERY;
                List<Title> frames = connecting ? connectingFrames : waitingFrames;
                if (!frames.isEmpty()) {
                    int frame = connecting ? connectingFrame : waitingFrame;
                    player.showTitle(frames.get(frame % frames.size()));
                    if (!connecting) {
                        config.sounds().waiting().ifPresent(sound -> player.playSound(sound, Sound.Emitter.self()));
                    }
                } else {
                    player.resetTitle();
                }
            }));
        }

        if (!waitingFrames.isEmpty()) {
            waitingFrame = (waitingFrame + 1) % waitingFrames.size();
        }
        if (!connectingFrames.isEmpty()) {
            connectingFrame = (connectingFrame + 1) % connectingFrames.size();
        }
    }
}
