/*******************************************************************************
 * This file is part of ASkyBlock.
 *
 *     ASkyBlock is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     ASkyBlock is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with ASkyBlock.  If not, see <http://www.gnu.org/licenses/>.
 *******************************************************************************/
package com.wasteofplastic.askyblock.listeners;

import java.util.HashSet;
import java.util.UUID;

import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import com.wasteofplastic.askyblock.ASkyBlock;
import com.wasteofplastic.askyblock.InventorySave;
import com.wasteofplastic.askyblock.Island;
import com.wasteofplastic.askyblock.Settings;
import com.wasteofplastic.askyblock.util.VaultHelper;

/**
 * @author tastybento
 *         Provides protection to islands
 */
public class PlayerEvents implements Listener {
    private final ASkyBlock plugin;
    private final boolean debug = false;
    // A set of falling players
    private static HashSet<UUID> fallingPlayers = new HashSet<UUID>();

    public PlayerEvents(final ASkyBlock plugin) {
	this.plugin = plugin;

    }


    /*
     * Prevent dropping items if player dies on another island
     * This option helps reduce the down side of dying due to traps, etc.
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = false)
    public void onVistorDeath(final PlayerDeathEvent e) {
	if (debug) {
	    plugin.getLogger().info(e.getEventName());
	}
	if (!IslandGuard.inWorld(e.getEntity())) {
	    return;
	}
	// If the player is on their island then they die and lose everything -
	// sorry :-(
	if (plugin.getGrid().playerIsOnIsland(e.getEntity())) {
	    return;
	}
	// If visitors will keep items and their level on death
	// This will override any global settings
	if (Settings.allowVisitorKeepInvOnDeath) {
	    InventorySave.getInstance().savePlayerInventory(e.getEntity());
	    e.getDrops().clear();
	    e.setKeepLevel(true);
	    e.setDroppedExp(0);
	}
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onVistorSpawn(final PlayerRespawnEvent e) {
	if (debug) {
	    plugin.getLogger().info(e.getEventName());
	}
	// This will override any global settings
	if (Settings.allowVisitorKeepInvOnDeath) {
	    InventorySave.getInstance().loadPlayerInventory(e.getPlayer());
	}
    }
    /*
     * Prevent item pickup by visitors
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onVisitorPickup(final PlayerPickupItemEvent e) {
	if (debug) {
	    plugin.getLogger().info(e.getEventName());
	}
	if (!IslandGuard.inWorld(e.getPlayer())) {
	    return;
	}
	if (Settings.allowVisitorItemPickup || e.getPlayer().isOp() || VaultHelper.checkPerm(e.getPlayer(), Settings.PERMPREFIX + "mod.bypassprotect")
		|| plugin.getGrid().locationIsOnIsland(e.getPlayer(), e.getItem().getLocation())) {
	    return;
	}
	e.setCancelled(true);
    }

    /*
     * Prevent item drop by visitors
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onVisitorDrop(final PlayerDropItemEvent e) {
	if (debug) {
	    plugin.getLogger().info(e.getEventName());
	}
	if (!IslandGuard.inWorld(e.getPlayer())) {
	    return;
	}
	if (Settings.allowVisitorItemPickup || e.getPlayer().isOp() || VaultHelper.checkPerm(e.getPlayer(), Settings.PERMPREFIX + "mod.bypassprotect")
		|| plugin.getGrid().locationIsOnIsland(e.getPlayer(), e.getItemDrop().getLocation())) {
	    return;
	}
	e.getPlayer().sendMessage(ChatColor.RED + plugin.myLocale(e.getPlayer().getUniqueId()).islandProtected);
	e.setCancelled(true);
    }


    /*
     * Prevent typing /island if falling - hard core
     * Checked if player teleports
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerFall(final PlayerMoveEvent e) {
	if (e.getPlayer().isDead()) {
	    return;
	}
	/*
	 * too spammy
	 * if (debug) {
	 * plugin.getLogger().info(e.getEventName());
	 * }
	 */
	if (!IslandGuard.inWorld(e.getPlayer())) {
	    return;
	}
	if (Settings.allowTeleportWhenFalling) {
	    return;
	}
	if (!e.getPlayer().getGameMode().equals(GameMode.SURVIVAL) || e.getPlayer().isOp()) {
	    return;
	}
	// Check if air below player
	// plugin.getLogger().info("DEBUG:" +
	// Math.round(e.getPlayer().getVelocity().getY()));
	if ((Math.round(e.getPlayer().getVelocity().getY()) < 0L)
		&& e.getPlayer().getLocation().getBlock().getRelative(BlockFace.DOWN).getType() == Material.AIR
		&& e.getPlayer().getLocation().getBlock().getType() == Material.AIR) {
	    // plugin.getLogger().info("DEBUG: falling");
	    setFalling(e.getPlayer().getUniqueId());
	} else {
	    // plugin.getLogger().info("DEBUG: not falling");
	    unsetFalling(e.getPlayer().getUniqueId());
	}
    }

    /**
     * Prevents teleporting when falling based on setting by stopping commands
     * 
     * @param e
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerTeleport(final PlayerCommandPreprocessEvent e) {
	if (debug) {
	    plugin.getLogger().info(e.getEventName());
	}
	if (!IslandGuard.inWorld(e.getPlayer()) || Settings.allowTeleportWhenFalling || e.getPlayer().isOp()
		|| !e.getPlayer().getGameMode().equals(GameMode.SURVIVAL)) {
	    return;
	}
	// Check commands
	// plugin.getLogger().info("DEBUG: falling command: '" +
	// e.getMessage().substring(1).toLowerCase() + "'");
	if (isFalling(e.getPlayer().getUniqueId()) && Settings.fallingCommandBlockList.contains(e.getMessage().substring(1).toLowerCase())) {
	    // Sorry you are going to die
	    e.getPlayer().sendMessage(plugin.myLocale(e.getPlayer().getUniqueId()).islandcannotTeleport);
	    e.setCancelled(true);
	}
    }

    /**
     * Prevents teleporting when falling based on setting and teleporting to locked islands
     * 
     * @param e
     */
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlayerTeleport(final PlayerTeleportEvent e) {
	if (debug) {
	    plugin.getLogger().info(e.getEventName());
	}
	if (e.getTo() == null || !IslandGuard.inWorld(e.getTo())) {
	    return;
	}
	// Check if ready
	if (plugin.getGrid() == null) {
	    return;
	}
	// Teleporting while falling check
	if (!Settings.allowTeleportWhenFalling && e.getPlayer().getGameMode().equals(GameMode.SURVIVAL) && !e.getPlayer().isOp()) {
	    if (isFalling(e.getPlayer().getUniqueId())) {
		// Sorry you are going to die
		e.getPlayer().sendMessage(plugin.myLocale(e.getPlayer().getUniqueId()).islandcannotTeleport);
		e.setCancelled(true);
		// Check if the player is in the void and kill them just in case
		if (e.getPlayer().getLocation().getBlockY() < 0) {
		    e.getPlayer().setHealth(0D);
		    unsetFalling(e.getPlayer().getUniqueId());
		}
		return;
	    }
	}
	//plugin.getLogger().info("DEBUG: From : " + e.getFrom());
	//plugin.getLogger().info("DEBUG: To : " + e.getTo());
	// Teleporting to a locked island
	Island islandTo = plugin.getGrid().getProtectedIslandAt(e.getTo());
	// Announcement entering
	Island islandFrom = plugin.getGrid().getProtectedIslandAt(e.getFrom());
	// Only says something if there is a change in islands
	/*
	 * Teleport Situations:
	 * islandTo == null && islandFrom != null - exit
	 * islandTo == null && islandFrom == null - nothing
	 * islandTo != null && islandFrom == null - enter
	 * islandTo != null && islandFrom != null - same PlayerIsland or teleport?
	 * islandTo == islandFrom
	 */
	if (islandTo != null && islandTo.getOwner() != null) {
	    if (islandTo != islandFrom) {
		// Entering
		if (islandTo.isLocked() || plugin.getPlayers().isBanned(islandTo.getOwner(),e.getPlayer().getUniqueId())) {
		    e.getPlayer().sendMessage(ChatColor.RED + plugin.myLocale(e.getPlayer().getUniqueId()).lockIslandLocked);
		    if (!plugin.getGrid().locationIsOnIsland(e.getPlayer(), e.getTo()) && !e.getPlayer().isOp()
			    && !VaultHelper.checkPerm(e.getPlayer(), Settings.PERMPREFIX + "mod.bypassprotect")) {
			e.setCancelled(true);
			return;
		    }
		}
		e.getPlayer().sendMessage(plugin.myLocale(e.getPlayer().getUniqueId()).lockNowEntering.replace("[name]", plugin.getPlayers().getName(islandTo.getOwner())));
	    }
	}
    }



 
    /**
     * Used to prevent teleporting when falling
     * 
     * @param uniqueId
     * @return true or false
     */
    public static boolean isFalling(UUID uniqueId) {
	return fallingPlayers.contains(uniqueId);
    }

    /**
     * Used to prevent teleporting when falling
     * 
     * @param uniqueId
     */
    public static void setFalling(UUID uniqueId) {
	fallingPlayers.add(uniqueId);
    }

    /**
     * Used to prevent teleporting when falling
     * 
     * @param uniqueId
     */
    public static void unsetFalling(UUID uniqueId) {
	// getLogger().info("DEBUG: unset falling");
	fallingPlayers.remove(uniqueId);
    }
}