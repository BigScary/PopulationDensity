package me.ryanhamshire.PopulationDensity;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Rabbit;
import org.bukkit.entity.Tameable;
import org.bukkit.material.Colorable;

public class MonitorPerformanceTask implements Runnable 
{
    static Long lastExecutionTimestamp = null;
    
    //runs every 1200 ticks (about 1 minute)
    @Override
    public void run() 
	{
	    if(lastExecutionTimestamp == null)
	    {
	        lastExecutionTimestamp = System.currentTimeMillis();
	        return;
	    }
	    
	    long now = System.currentTimeMillis();
	    long millisecondsSinceLastExecution = now - lastExecutionTimestamp;
	    
	    float tps = 1200 / (millisecondsSinceLastExecution / 1000f);
	    
	    treatLag(tps);
	    
	    lastExecutionTimestamp = now;
	}
    
    public static void treatLag(float tps)
    {
	    int minutesLagging = 0;
	    boolean stopGrinders = false;
	    boolean bootIdlePlayers = false;
	    boolean removeEntities = false; 
	    
	    if(tps > 19)
	    {
	        //if we were lagging but aren't anymore, stop collecting performance data
	        if(PopulationDensity.instance.config_captureSpigotTimingsWhenLagging && PopulationDensity.instance.isSpigotServer && minutesLagging >= 5)
	        {
	            Bukkit.getServer().dispatchCommand(Bukkit.getServer().getConsoleSender(), "timings off");
	        }
	        
	        minutesLagging = 0;
	    }
	    else
	    {
	        minutesLagging = PopulationDensity.minutesLagging + 1;
	        if(PopulationDensity.instance.config_bootIdlePlayersWhenLagging)
	        {
	            bootIdlePlayers = true;
	        }
	        
	        if(PopulationDensity.instance.config_captureSpigotTimingsWhenLagging && PopulationDensity.instance.isSpigotServer)
	        {
    	        //if lagging at least 5 minutes, start collecting performance data
	            if(minutesLagging == 5)
    	        {
    	            Bukkit.getServer().dispatchCommand(Bukkit.getServer().getConsoleSender(), "timings on");
    	        }
    	        
    	        //if lagging for 10 minutes, generate a performance report
                else if(minutesLagging == 10)
    	        {
    	            Bukkit.getServer().dispatchCommand(Bukkit.getServer().getConsoleSender(), "timings paste");
    	        }
	        }
	    }
	    
	    if(tps <= 19)
	    {
	        PopulationDensity.minutesLagging += 1;
	        if(PopulationDensity.instance.config_disableGrindersWhenLagging)
	        {
	            stopGrinders = true;
	        }
	    }
	    
	    if(tps <= 16 || minutesLagging >= 5)
	    {
	        removeEntities = true;
	    }
	    	    
	    PopulationDensity.minutesLagging = minutesLagging;
	    
	    PopulationDensity.bootingIdlePlayersForLag = bootIdlePlayers;
	    PopulationDensity.grindersStopped = stopGrinders;
	    
	    if(removeEntities && PopulationDensity.instance.thinAnimalAndMonsterCrowds)
	    {
	        thinEntities();
	    }
	    
	    PopulationDensity.serverTicksPerSecond = tps;
	}
    
    @SuppressWarnings("deprecation")
    static void thinEntities()
    {
        //thinnable entity types
        final HashSet<EntityType> thinnableAnimals = new HashSet<EntityType>(Arrays.asList
        (
            EntityType.COW,
            EntityType.HORSE,
            EntityType.CHICKEN,
            EntityType.SHEEP,
            EntityType.PIG,
            EntityType.WOLF,
            EntityType.OCELOT,
            EntityType.RABBIT,
            EntityType.MUSHROOM_COW
        ));
        
        int totalEntities = 0;
        int totalRemoved = 0;
        HashMap<String, Integer> totalEntityCounter = new HashMap<String, Integer>();
        for(World world : PopulationDensity.instance.getServer().getWorlds())
        {
            Environment environment = world.getEnvironment();
            HashSet<Material> allowedSpawnSurfaces = EntityEventHandler.allowedSpawnBlocks.get(environment);
            
            for(Chunk chunk : world.getLoadedChunks())
            {
                HashMap<String, Integer> chunkEntityCounter = new HashMap<String, Integer>();
                
                Entity [] entities = chunk.getEntities();
                int monsterCount = 0;
                boolean removedAnimalThisPass = false;
                for(Entity entity : entities)
                {
                    EntityType entityType = entity.getType();
                    String entityTypeID = entity.getType().name();
                    if(entityType == EntityType.SHEEP)
                    {
                        Colorable colorable = (Colorable)entity;
                        entityTypeID += colorable.getColor().name();
                    }
                    else if(entityType == EntityType.RABBIT)
                    {
                        Rabbit rabbit = (Rabbit)entity;
                        entityTypeID += rabbit.getRabbitType().name(); 
                    }
                    
                    if(entity instanceof LivingEntity) totalEntities++;
                    
                    //skip any pets
                    if(entity instanceof Tameable)
                    {
                        if(((Tameable)entity).isTamed()) continue;
                    }
                    
                    //skip any entities with nameplates
                    if(entity.getCustomName() != null && entity.getCustomName() != "") continue;
                    
                    //only specific types of animals may be removed
                    boolean isAnimal = entity instanceof Animals;
                    EntityType type = entity.getType();
                    if(isAnimal && !thinnableAnimals.contains(type)) continue;
                    
                    Integer count = chunkEntityCounter.get(entityTypeID);
                    if(count == null) count = 0;
                    
                    chunkEntityCounter.put(entityTypeID, count + 1);
                    
                    if(type == EntityType.EXPERIENCE_ORB)
                    {
                        if(count > 15)
                        {
                            entity.remove();
                            totalRemoved++;
                        }
                    }
                    else if(type == EntityType.DROPPED_ITEM)
                    {
                        if(count > 25)
                        {
                            entity.remove();
                            totalRemoved++;
                        }
                    }
                    else if(type == EntityType.BOAT)
                    {
                        if(count > 5)
                        {
                            entity.remove();
                            totalRemoved++;
                        }
                    }
                    else if(entity instanceof Monster)
                    {
                        if(++monsterCount > 2)
                        {
                            entity.remove();
                            totalRemoved++;
                        }
                        else
                        {
                            Material underType = entity.getLocation().getBlock().getRelative(BlockFace.DOWN).getType();
                            if(!allowedSpawnSurfaces.contains(underType))
                            {
                                entity.remove();
                                totalRemoved++;
                            }
                        }
                    }
                    else if(isAnimal)
                    {
                        if(count > 20 || (count > 5 && !removedAnimalThisPass) || entity.getLocation().getBlock().getLightFromBlocks() < 4)
                        {
                            ((Animals) entity).setHealth(0);
                            removedAnimalThisPass = true;
                            totalRemoved++;
                            
                            if(PopulationDensity.instance.markRemovedEntityLocations)
                            {
                                Block block = entity.getLocation().getBlock();
                                Material blockType = block.getType();
                                if(blockType == Material.LONG_GRASS || blockType == Material.AIR)
                                {
                                    block.setTypeIdAndData(31, (byte)0, false);  //dead bush
                                }
                            }
                        }
                    }
                    else if(type == EntityType.PIG_ZOMBIE && entity.getWorld().getEnvironment() != Environment.NETHER)
                    {
                        entity.remove();
                        totalRemoved++;
                    }
                }
            
                for(String key : chunkEntityCounter.keySet())
                {
                    Integer totalCounted = totalEntityCounter.get(key);
                    if(totalCounted == null) totalCounted = 0;
                    Integer chunkCounted = chunkEntityCounter.get(key);
                    totalCounted += chunkCounted;
                    totalEntityCounter.put(key, totalCounted);
                }
            }
        }
        
        PopulationDensity.AddLogEntry("Removed " + totalRemoved + " of " + totalEntities + " entities.");
        
        if(PopulationDensity.minutesLagging > 5 && PopulationDensity.minutesLagging % 6 == 0)
        {
            PopulationDensity.AddLogEntry("Still lagging after thinning entities.  Remaining entities by chunk and type:");
            PopulationDensity.AddLogEntry("world;chunkx;chunkz;type;count");
            for(World world : Bukkit.getServer().getWorlds())
            {
                for(Chunk chunk : world.getLoadedChunks())
                {
                    BlockState [] blocks = chunk.getTileEntities();
                    int total = 0;
                    ConcurrentHashMap<String, Integer> entityCounter = new ConcurrentHashMap<String, Integer>();
                    for(BlockState block : blocks)
                    {
                        String typeName = block.getType().name();
                        Integer oldValue = entityCounter.get(typeName);
                        if(oldValue == null) oldValue = 0;
                        entityCounter.put(typeName, oldValue + 1);
                        total++;
                    }
                    
                    Entity[] entities = chunk.getEntities();
                    for(Entity entity : entities)
                    {
                        String typeName = entity.getType().name();
                        Integer oldValue = entityCounter.get(typeName);
                        if(oldValue == null) oldValue = 0;
                        entityCounter.put(typeName, oldValue + 1);
                        total++;
                    }
                    
                    for(String typeName : entityCounter.keySet())
                    {
                        PopulationDensity.AddLogEntry(";" + world.getName() + ";" + chunk.getX() + ";" + chunk.getZ() + ";" + typeName + ";" + entityCounter.get(typeName));
                    }
                    
                    if(total > 10)
                    {
                        PopulationDensity.AddLogEntry(";" + world.getName() + ";" + chunk.getX() + ";" + chunk.getZ() + ";TOTAL;" + total);
                    }
                }
            }
        }
    }
}
