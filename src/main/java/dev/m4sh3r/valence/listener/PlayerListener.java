package dev.m4sh3r.valence.listener;

import dev.m4sh3r.valence.Valence;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Set;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class PlayerListener implements Listener {

    private static final Set<PotionEffectType> HARMFUL = Set.of(
            PotionEffectType.INSTANT_DAMAGE, PotionEffectType.POISON, PotionEffectType.WITHER,
            PotionEffectType.WEAKNESS, PotionEffectType.SLOWNESS, PotionEffectType.BLINDNESS,
            PotionEffectType.NAUSEA, PotionEffectType.HUNGER, PotionEffectType.MINING_FATIGUE,
            PotionEffectType.DARKNESS, PotionEffectType.LEVITATION);

    private final Valence plugin;

    public PlayerListener(Valence plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.teams().handleJoin(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.teams().handleQuit(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        plugin.teams().recordDeath(victim.getUniqueId());
        Player killer = victim.getKiller();
        if (killer != null && !killer.equals(victim)) {
            plugin.teams().recordKill(killer.getUniqueId());
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Player attacker = attacker(event);
        if (attacker == null || attacker.equals(victim)) {
            return;
        }
        if (!plugin.teams().canHurt(attacker.getUniqueId(), victim.getUniqueId())) {
            event.setCancelled(true);
            plugin.messages().actionBar(attacker, "friendly-fire.blocked");
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onSplash(PotionSplashEvent event) {
        if (!(event.getPotion().getShooter() instanceof Player thrower)) {
            return;
        }
        boolean harmful = false;
        for (PotionEffect effect : event.getPotion().getEffects()) {
            if (HARMFUL.contains(effect.getType())) {
                harmful = true;
                break;
            }
        }
        if (!harmful) {
            return;
        }
        for (LivingEntity entity : event.getAffectedEntities()) {
            if (entity instanceof Player victim && !victim.equals(thrower)
                    && !plugin.teams().canHurt(thrower.getUniqueId(), victim.getUniqueId())) {
                event.setIntensity(victim, 0);
            }
        }
    }

    private Player attacker(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof Projectile projectile && projectile.getShooter() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof Tameable pet && pet.getOwner() instanceof Player player) {
            return player;
        }
        return null;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        boolean team = plugin.teams().teamChat(player.getUniqueId());
        boolean ally = plugin.teams().allyChat(player.getUniqueId());
        if ((!team && !ally) || plugin.teams().teamOf(player.getUniqueId()) == null) {
            return;
        }
        event.setCancelled(true);
        String text = PlainTextComponentSerializer.plainText().serialize(event.message());
        if (ally) {
            plugin.teams().sendAllyChat(player, text);
        } else {
            plugin.teams().sendTeamChat(player, text);
        }
    }
}
