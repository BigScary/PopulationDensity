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

import java.util.Random;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Creeper;
import org.bukkit.entity.Enderman;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Spider;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.inventory.ItemStack;

public class EntityEventHandler implements Listener
{
	//when an entity (includes both dynamite and creepers) explodes...
	@EventHandler(ignoreCancelled = true)
	public void onEntityExplode(EntityExplodeEvent explodeEvent)
	{		
		Location location = explodeEvent.getLocation();
		
		//if it's NOT in the managed world, let it splode (or let other plugins worry about it)
		RegionCoordinates region = RegionCoordinates.fromLocation(location);
		if(region == null) return;
		
		//otherwise if it's close to a region post
		Location regionCenter = PopulationDensity.getRegionCenter(region);
		regionCenter.setY(PopulationDensity.ManagedWorld.getHighestBlockYAt(regionCenter));		
		if(regionCenter.distanceSquared(location) < 225)  //225 = 15 * 15
		{			
			explodeEvent.blockList().clear(); //All the noise and terror, none of the destruction (whew!).
		}
		
		//NOTE!  Why not distance?  Because distance squared is cheaper and will be good enough for this.
	}
	
	//when an item despawns
	//FEATURE: in the newest region only, regrow trees from fallen saplings
	@EventHandler(ignoreCancelled = true)
	public void onItemDespawn (ItemDespawnEvent event)
	{
		//respect config option
		if(!PopulationDensity.instance.regrowTrees) return;
		
		//only care about dropped items
		Entity entity = event.getEntity();
		if(entity.getType() != EntityType.DROPPED_ITEM) return;
		
		if(!(entity instanceof Item)) return;
		
		//get info about the dropped item
		ItemStack item = ((Item)entity).getItemStack();
		
		//only care about saplings
		if(item.getType() != Material.SAPLING) return;
		
		//only care about the newest region
		if(!PopulationDensity.instance.dataStore.getOpenRegion().equals(RegionCoordinates.fromLocation(entity.getLocation()))) return;
		
		//only replace these blocks with saplings
		Block block = entity.getLocation().getBlock();
		if(block.getType() != Material.AIR && block.getType() != Material.LONG_GRASS && block.getType() != Material.SNOW) return;
		
		//only plant trees in grass or dirt
		Block underBlock = block.getRelative(BlockFace.DOWN);
		if(underBlock.getType() == Material.GRASS || underBlock.getType() == Material.DIRT)
		{
			block.setTypeIdAndData(item.getTypeId(), item.getData().getData(), false);
		}
	}	
	
	@EventHandler(ignoreCancelled = true)
	public void onEntityDamage (EntityDamageEvent event)
	{
		if(!(event instanceof EntityDamageByEntityEvent)) return;
		
		EntityDamageByEntityEvent subEvent = (EntityDamageByEntityEvent) event;
		
		Player attacker = null;
		Entity damageSource = subEvent.getDamager();
		if(damageSource instanceof Player)
		{
			attacker = (Player)damageSource;
		}
		else if(damageSource instanceof Arrow)
		{
			Arrow arrow = (Arrow)damageSource;
			if(arrow.getShooter() instanceof Player)
			{
				attacker = (Player)arrow.getShooter();
			}
		}
		else if(damageSource instanceof ThrownPotion)
		{
			ThrownPotion potion = (ThrownPotion)damageSource;
			if(potion.getShooter() instanceof Player)
			{
				attacker = (Player)potion.getShooter();
			}
		}
		
		if(attacker != null)
		{
			PopulationDensity.instance.resetIdleTimer(attacker);
		}		
	}
	
	private int respawnAnimalCounter = 1;
	
	@EventHandler(ignoreCancelled = true)
	public void onEntitySpawn(CreatureSpawnEvent event)
	{
		//do nothing for non-natural spawns
		if(event.getSpawnReason() != SpawnReason.NATURAL) return;
		
		//when an animal naturally spawns, grow grass around it
		Entity entity = event.getEntity();
		if(entity instanceof Animals && PopulationDensity.instance.regrowGrass)
		{
			this.regrow(entity.getLocation().getBlock(), 8);
		}
		
		//when a monster spawns, sometimes spawn animals too
		if(entity instanceof Monster && PopulationDensity.instance.respawnAnimals)
		{
			//only do this if the spawn is in the newest region
			if(!PopulationDensity.instance.dataStore.getOpenRegion().equals(RegionCoordinates.fromLocation(entity.getLocation()))) return;				
			
			//if it's on grass, there's a 1/100 chance it will also spawn a group of animals
			Block underBlock = event.getLocation().getBlock().getRelative(BlockFace.DOWN);
			if(underBlock.getType() == Material.GRASS && --this.respawnAnimalCounter == 0)
			{
				this.respawnAnimalCounter = 50;
				EntityType animalType = null;
				
				//decide what to spawn based on the type of monster
				if(entity instanceof Creeper)
				{
					animalType = EntityType.COW;
				}
				else if(entity instanceof Zombie)
				{
					animalType = EntityType.CHICKEN;
				}
				else if(entity instanceof Spider)
				{
					animalType = EntityType.PIG;
				}
				else if(entity instanceof Enderman)
				{
					animalType = EntityType.SHEEP;
				}
				
				//spawn three animals at the entity's location and regrow some grass
				if(animalType != null)
				{
					for(int i = 0; i < 3; i++)
					{
						entity.getWorld().spawnEntity(entity.getLocation(), animalType);
					}
					
					this.regrow(entity.getLocation().getBlock(), 8);
				}
			}
		}
	}
	
	private void regrow(Block center, int radius){
        Random rnd = new Random();
        int radius_squared = radius * radius;
        Block toHandle;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                toHandle = center.getWorld().getBlockAt(center.getX() + x, center.getWorld().getMaxHeight() - 1, center.getZ() + z);
                while(toHandle.getType() == Material.AIR) toHandle = toHandle.getRelative(BlockFace.DOWN);
                if (toHandle.getType() == Material.GRASS) { // Block is grass
                    if (center.getLocation().distanceSquared(toHandle.getLocation()) <= radius_squared) { // Block is in radius
                        if (rnd.nextInt(100) < 66) {    // Random chance
                            toHandle.getRelative(BlockFace.UP).setType(Material.LONG_GRASS);
                            toHandle.getRelative(BlockFace.UP).setData((byte) 1);  //data == 1 means live grass instead of dead shrub
                        }
                    }
                }
            }
        }
    }
}
