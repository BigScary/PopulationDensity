/*
    PopulationDensity Server Plugin for Minecraft
    Copyright (C) 2011 Ryan Hamshire

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package me.ryanhamshire.PopulationDensity;

import java.util.Arrays;
import java.util.List;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDamageEvent;
import org.bukkit.event.block.BlockPlaceEvent;

public class BlockEventHandler implements Listener 
{
    private static List<Material> alwaysBreakableMaterials = Arrays.asList(
        Material.LONG_GRASS,
        Material.DOUBLE_PLANT,
        Material.LOG,
        Material.LOG_2,
        Material.LEAVES,
        Material.LEAVES_2,
        Material.RED_ROSE,
        Material.YELLOW_FLOWER,
        Material.SNOW_BLOCK
    );
    
	//when a player breaks a block...
	@EventHandler(ignoreCancelled = true)
	public void onBlockBreak(BlockBreakEvent breakEvent)
	{
		Player player = breakEvent.getPlayer();
		
		PopulationDensity.instance.resetIdleTimer(player);
		
		Block block = breakEvent.getBlock();
		
		//if the player is not in managed world, do nothing (let vanilla code and other plugins do whatever)
		if(!player.getWorld().equals(PopulationDensity.ManagedWorld)) return;
		
		//otherwise figure out which region that block is in
		Location blockLocation = block.getLocation();
		
		//region posts are at sea level at the lowest, so no need to check build permissions under that
		if(blockLocation.getBlockY() < PopulationDensity.instance.minimumRegionPostY) return;
		
		//whitelist for blocks which can always be broken (grass cutting, tree chopping)
		if(BlockEventHandler.alwaysBreakableMaterials.contains(block.getType())) return;
		
		RegionCoordinates blockRegion = RegionCoordinates.fromLocation(blockLocation); 
		
		//if too close to (or above) region post, send an error message
		//note the ONLY way to edit around a region post is to have special permission
		if(!player.hasPermission("populationdensity.buildbreakanywhere") && this.nearRegionPost(blockLocation, blockRegion, PopulationDensity.instance.postProtectionRadius))
		{
			if(PopulationDensity.instance.buildRegionPosts)
				player.sendMessage("You can't break blocks this close to the region post.");
			else
				player.sendMessage("You can't break blocks this close to a player spawn point.");
			breakEvent.setCancelled(true);
			return;
		}
	}
	
	//COPY PASTE!  this is practically the same as the above block break handler
	@EventHandler(ignoreCancelled = true)
	public void onBlockPlace(BlockPlaceEvent placeEvent)
	{
		Player player = placeEvent.getPlayer();
		
		PopulationDensity.instance.resetIdleTimer(player);
		
		Block block = placeEvent.getBlock();
		
		//if not in managed world, do nothing
		if(!player.getWorld().equals(PopulationDensity.ManagedWorld)) return;
		
		Location blockLocation = block.getLocation();
		
		//region posts are at sea level at the lowest, so no need to check build permissions under that
		if(blockLocation.getBlockY() < PopulationDensity.instance.minimumRegionPostY) return;
		
		RegionCoordinates blockRegion = RegionCoordinates.fromLocation(blockLocation); 
		
		//if too close to (or above) region post, send an error message
		if(!player.hasPermission("populationdensity.buildbreakanywhere") && this.nearRegionPost(blockLocation, blockRegion, PopulationDensity.instance.postProtectionRadius))
		{
			if(PopulationDensity.instance.buildRegionPosts)
				player.sendMessage("You can't build this close to the region post.");
			else
				player.sendMessage("You can't build this close to a player spawn point.");
			placeEvent.setCancelled(true);
			return;
		}
		
		//if bed or chest and player has not been reminded about /movein this play session
		if(block.getType() == Material.BED || block.getType() == Material.CHEST)
		{
			PlayerData playerData = PopulationDensity.instance.dataStore.getPlayerData(player);
			if(playerData.advertisedMoveInThisSession) return;
			
			if(!playerData.homeRegion.equals(blockRegion))
			{
				player.sendMessage("You're building outside of your home region.  If you'd like to make this region your new home to help you return here later, use /MoveIn.");
				playerData.advertisedMoveInThisSession = true;
			}
		}
	}
	
	//when a player damages a block...
    @EventHandler(ignoreCancelled = true)
    public void onBlockDamage(BlockDamageEvent damageEvent)
    {
        Player player = damageEvent.getPlayer();
        
        Block block = damageEvent.getBlock();
        if(player == null || (block.getType() != Material.WALL_SIGN && block.getType() != Material.SIGN_POST)) return;
        
        //if the player is not in managed world, do nothing
        if(!player.getWorld().equals(PopulationDensity.ManagedWorld)) return;
        
        if(!this.nearRegionPost(block.getLocation(), RegionCoordinates.fromLocation(block.getLocation()), 1)) return;
        
        player.sendMessage(ChatColor.GREEN + "Region post help and commands: " + ChatColor.AQUA + "http://bit.ly/mcregions");
    }
	
	//determines whether or not you're "near" a region post
	private boolean nearRegionPost(Location location, RegionCoordinates region, int howClose)
	{
		Location postLocation = PopulationDensity.getRegionCenter(region);
		
		//NOTE!  Why not use distance?  Because I want a box to the sky, not a sphere.
		//Why not round?  Below calculation is cheaper than distance (needed for a cylinder or sphere).
		//Why to the sky?  Because if somebody builds a platform above the post, folks will teleport onto that platform by mistake.
		//Also...  lava from above would be bad.
		//Why not below?  Because I can't imagine mining beneath a post as an avenue for griefing. 
		
		return (	location.getBlockX() >= postLocation.getBlockX() - howClose &&
					location.getBlockX() <= postLocation.getBlockX() + howClose &&
					location.getBlockZ() >= postLocation.getBlockZ() - howClose &&
					location.getBlockZ() <= postLocation.getBlockZ() + howClose &&
					location.getBlockY() >= PopulationDensity.ManagedWorld.getHighestBlockYAt(postLocation) - 4
				);
	}
}
