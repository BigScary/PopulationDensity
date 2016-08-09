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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World.Environment;
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
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Spider;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.inventory.ItemStack;

public class EntityEventHandler implements Listener
{
    //block types monsters may spawn on when grinders are disabled
    static HashMap<Environment, HashSet<Material>> allowedSpawnBlocks;  
    
	public EntityEventHandler()
	{
	    if(allowedSpawnBlocks == null)
	    {
	        allowedSpawnBlocks = new HashMap<Environment, HashSet<Material>>();
	    
	        allowedSpawnBlocks.put(Environment.NORMAL, new HashSet<Material>(Arrays.asList(
    	        Material.GRASS,
    	        Material.SAND,
    	        Material.GRAVEL,
    	        Material.STONE,
    	        Material.MOSSY_COBBLESTONE,
    	        Material.OBSIDIAN)));
    	    
    	    allowedSpawnBlocks.put(Environment.NETHER, new HashSet<Material>(Arrays.asList(
                Material.NETHERRACK,
                Material.NETHER_BRICK)));
    	    
    	    allowedSpawnBlocks.put(Environment.THE_END, new HashSet<Material>(Arrays.asList(
                Material.ENDER_STONE,
                Material.OBSIDIAN)));
	    }
	}
    
    //when an entity (includes both dynamite and creepers) explodes...
	@EventHandler(ignoreCancelled = true)
	public void onEntityExplode(EntityExplodeEvent explodeEvent)
	{		
		Location location = explodeEvent.getLocation();
		
		//if it's NOT in the managed world, let it splode (or let other plugins worry about it)
		RegionCoordinates region = RegionCoordinates.fromLocation(location);
		if(region == null) return;
		
		//otherwise if it's close to a region post
		Location regionCenter = PopulationDensity.getRegionCenter(region, false);
		regionCenter.setY(PopulationDensity.ManagedWorld.getHighestBlockYAt(regionCenter));		
		if(regionCenter.distanceSquared(location) < 225)  //225 = 15 * 15
		{			
			explodeEvent.blockList().clear(); //All the noise and terror, none of the destruction (whew!).
		}
		
		//NOTE!  Why not distance?  Because distance squared is cheaper and will be good enough for this.
	}
	
	//when an item despawns
	//FEATURE: in the newest region only, regrow trees from fallen saplings
	@SuppressWarnings("deprecation")
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
		
		//don't plant saplings next to other saplings or logs
		Block [] neighbors = new Block [] { 				
				block.getRelative(BlockFace.EAST), 
				block.getRelative(BlockFace.WEST), 
				block.getRelative(BlockFace.NORTH), 
				block.getRelative(BlockFace.SOUTH), 
				block.getRelative(BlockFace.NORTH_EAST), 
				block.getRelative(BlockFace.SOUTH_EAST), 
				block.getRelative(BlockFace.SOUTH_WEST), 
				block.getRelative(BlockFace.NORTH_WEST) };
		
		for(int i = 0; i < neighbors.length; i++)
		{
			if(neighbors[i].getType() == Material.SAPLING || neighbors[i].getType() == Material.LOG) return;
		}
		
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
	    Entity entity = event.getEntity();
	    
	    //when an entity has fall damage immunity, it lasts for only ONE fall damage check
        if(event.getCause() == DamageCause.FALL)
        {
            if(PopulationDensity.instance.isFallDamageImmune(entity))
            {
                event.setCancelled(true);
                PopulationDensity.instance.removeFallDamageImmunity(entity);
                if(entity.getType() == EntityType.PLAYER)
                {
                    Player player = (Player)entity;
                    if(!player.hasPermission("populationdensity.teleportanywhere"))
                    {
                        player.getWorld().createExplosion(player.getLocation(), 0);
                    }
                }
            }
        }
	    
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
	
	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void onEntitySpawn(CreatureSpawnEvent event)
	{
	    SpawnReason reason = event.getSpawnReason();
	    Entity entity = event.getEntity();
	    
	    //if lag has prompted PD to turn off monster grinders, limit spawns
	    if(PopulationDensity.grindersStopped)
		{
		    if(reason == SpawnReason.NETHER_PORTAL || reason == SpawnReason.SPAWNER)
		    {
		        event.setCancelled(true);
		        return;
		    }
		    
		    else if(reason == SpawnReason.NATURAL && entity instanceof Monster)
		    {
		        HashSet<Material> allowedBlockTypes = allowedSpawnBlocks.get(entity.getWorld().getEnvironment());
		        if(!allowedBlockTypes.contains(entity.getLocation().getBlock().getRelative(BlockFace.DOWN).getType()))
		        {
		            event.setCancelled(true);
		            return;
		        }
		    }
		}
	    
	    //speed limit on monster grinder spawn rates - only affects grinders that rely on naturally-spawning monsters.
	    if(reason != SpawnReason.SPAWNER_EGG && reason != SpawnReason.SPAWNER && entity instanceof Monster)
	    {
	        int monstersNearby = 0;
	        List<Entity> entities = entity.getNearbyEntities(10,  20,  10);
	        for(Entity nearbyEntity : entities)
	        {
	            if(nearbyEntity instanceof Monster) monstersNearby++;
	            if(monstersNearby > PopulationDensity.instance.nearbyMonsterSpawnLimit)
	            {
	                event.setCancelled(true);
	                return;
	            }
	        }
	    }
	    
	    //natural spawns may cause animal spawns to keep new player resources available
	    if(reason == SpawnReason.NATURAL)
		{
    		if(PopulationDensity.ManagedWorld == null || event.getLocation().getWorld() != PopulationDensity.ManagedWorld) return;
    		
    		//when an animal naturally spawns, grow grass around it
    		if(entity instanceof Animals && PopulationDensity.instance.regrowGrass)
    		{
    			this.regrow(entity.getLocation().getBlock(), 4);
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
    				this.respawnAnimalCounter = 5;
    				
    				//check for other nearby animals
    				List<Entity> entities = entity.getNearbyEntities(30, 30, 30);
    				for(int i = 0; i < entities.size(); i++)
    				{
    					if(entity instanceof Animals) return;
    				}
    				
    				EntityType animalType = null;
    				
    				//decide what to spawn based on the type of monster
    				if(entity instanceof Creeper)
    				{
    				    animalType = EntityType.CHICKEN;
    				}
    				else if(entity instanceof Zombie)
    				{
    				    animalType = EntityType.COW;
    				}
    				else if(entity instanceof Spider)
    				{
    				    animalType = EntityType.SHEEP;
    				}
    				else if(entity instanceof Skeleton)
    				{
    				    animalType = EntityType.PIG;
    				}
    				else if(entity instanceof Enderman)
                    {
                        if(Math.random() > 0.5)
                            animalType = EntityType.HORSE;
                        else
                            animalType = EntityType.WOLF;
                    }
    				
    				//spawn an animal at the entity's location and regrow some grass
    				if(animalType != null)
    				{
    					entity.getWorld().spawnEntity(entity.getLocation(), animalType);
    					this.regrow(entity.getLocation().getBlock(), 4);
    				}
    			}
    		}
		}
	}
	
	@SuppressWarnings("deprecation")
    private void regrow(Block center, int radius)
	{
        Block toHandle;
        for (int x = -radius; x <= radius; x++)
        {
            for (int z = -radius; z <= radius; z++)
            {
                toHandle = center.getWorld().getBlockAt(center.getX() + x, center.getY() + 2, center.getZ() + z);
                while(toHandle.getType() == Material.AIR && toHandle.getY() > center.getY() - 4) toHandle = toHandle.getRelative(BlockFace.DOWN);
                if (toHandle.getType() == Material.GRASS) // Block is grass
                {
                    Block aboveBlock = toHandle.getRelative(BlockFace.UP);
                    if(aboveBlock.getType() == Material.AIR)
                    {
                    	aboveBlock.setType(Material.LONG_GRASS);
                        aboveBlock.setData((byte) 1);  //data == 1 means live grass instead of dead shrub
                    }
                    continue;
                }
            }
        }
    }
	
	@EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onEntityToggleFlight(EntityToggleGlideEvent event)
    {
        if(event.getEntityType() != EntityType.PLAYER) return;
	    
	    if(PopulationDensity.instance.isFallDamageImmune((Player)event.getEntity()))
        {
            event.setCancelled(true);
        }
    }
}
