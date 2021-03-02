package me.frog.reachdetect;

import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import java.beans.IntrospectionException;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static java.lang.Math.abs;

public final class ReachDetect extends JavaPlugin implements Listener {

    private double base_dis;
    private double measure_error;
    private Integer punishLimit;
    private Integer secondsTerminate;
    private double pingSoftener;
    private boolean sendPrivMsgs;
    private boolean publicAnnounce;
    private boolean needBanning;
    private Integer banDura;
    private boolean banIP;
    private Integer kicksToBan;
    private final Map<Player, Integer> reachedTimes = new HashMap<>();
    private final Map<Player, Integer> permOffences = new HashMap<>();
    private final Map<Player, Long> lastTime = new HashMap<>();

    @Override
    public void onEnable() {
        File configFile = new File(this.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            this.saveDefaultConfig();
        }
        base_dis = this.getConfig().getDouble("base-dist");
        measure_error = this.getConfig().getDouble("measure-error");
        punishLimit = this.getConfig().getInt("times-to-punish");
        secondsTerminate = this.getConfig().getInt("seconds-to-terminate");
        pingSoftener = this.getConfig().getDouble("ping-error");
        sendPrivMsgs = this.getConfig().getBoolean("send-priv-msgs");
        publicAnnounce = this.getConfig().getBoolean("announce-kicks");
        needBanning = this.getConfig().getBoolean("kick-or-ban");
        banDura = this.getConfig().getInt("ban-time-hours");
        banIP = this.getConfig().getBoolean("ban-IP");
        kicksToBan = this.getConfig().getInt("kicks-to-ban");
        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!reachedTimes.containsKey(event.getPlayer())) {
            reachedTimes.put(event.getPlayer(), 0);
            permOffences.put(event.getPlayer(), 0);
            lastTime.put(event.getPlayer(), 0L);
        }
    }

    public static int getPing(Player p) {
        String v = Bukkit.getServer().getClass().getPackage().getName().replace(".", ",").split(",")[3];
        if (!p.getClass().getName().equals("org.bukkit.craftbukkit." + v + ".entity.CraftPlayer")) {
            p = Bukkit.getPlayer(p.getUniqueId());
        }
        try {
            Class<?> CraftPlayerClass = Class.forName("org.bukkit.craftbukkit." + v + ".entity.CraftPlayer");
            Object CraftPlayer = CraftPlayerClass.cast((Player) p);
            Method getHandle = CraftPlayer.getClass().getMethod("getHandle");
            Object EntityPlayer = getHandle.invoke(CraftPlayer);
            Field ping = EntityPlayer.getClass().getDeclaredField("ping");
            return ping.getInt(EntityPlayer);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return 0;
    }

    @EventHandler
    public void onAttack(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player) {
            double dis = abs(event.getDamager().getLocation().distance(event.getEntity().getLocation()));
            if ((base_dis+measure_error+(pingSoftener*getPing((Player) event.getDamager()))) < dis) {
                if (sendPrivMsgs) {
                    event.getDamager().sendMessage("Your reach was greater then it should be.");
                }
                if (((Long) abs(System.currentTimeMillis()-lastTime.get(event.getDamager()))) > ((Long) (1000L*secondsTerminate))) {
                    reachedTimes.replace((Player) event.getDamager(), 0);
                    if (sendPrivMsgs) {
                        event.getDamager().sendMessage("Your reached attack count has reset.");
                    }
                }
                reachedTimes.replace((Player) event.getDamager(), reachedTimes.get(event.getDamager())+1);
                lastTime.replace((Player) event.getDamager(), System.currentTimeMillis());
                if (reachedTimes.get(event.getDamager()) > punishLimit) {
                    ((Player) event.getDamager()).kickPlayer("Reach hax");
                    permOffences.replace((Player) event.getDamager(), permOffences.get(event.getDamager())+1);
                    if (publicAnnounce) {
                        getServer().broadcast(((Player) event.getDamager()).getDisplayName()+"has been kicked for reach hax!", "");
                    }
                    if ((permOffences.get(event.getDamager()) > kicksToBan) && needBanning) {
                        Bukkit.getBanList(BanList.Type.NAME).addBan((event.getDamager()).getName(), "Reach hax", new Date(System.currentTimeMillis()+(1000L*3600L*banDura)), null);
                        if (banIP) {
                            Bukkit.getBanList(BanList.Type.IP).addBan(((Player) event.getDamager()).getAddress().toString(), "Reach hax", new Date(System.currentTimeMillis()+(1000L*3600L*banDura)), null);
                        }
                    }
                }
            }
        }
    }
}
