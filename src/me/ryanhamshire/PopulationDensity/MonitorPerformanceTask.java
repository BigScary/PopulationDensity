package me.ryanhamshire.PopulationDensity;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;

import org.bukkit.Chunk;
import org.bukkit.World;
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
	        minutesLagging = 0;
	    }
	    else
	    {
	        minutesLagging = PopulationDensity.minutesLagging + 1;
	        bootIdlePlayers = true;
	    }
	    
	    if(tps <= 19)
	    {
	        PopulationDensity.minutesLagging += 1;
	        stopGrinders = true;
	    }
	    
	    if(tps <= 16 || (minutesLagging >= 5 && minutesLagging % 2 == 1))
	    {
	        removeEntities = true;
	    }
	    	    
	    PopulationDensity.minutesLagging = minutesLagging;
	    
	    //if anything important is changing or happening, log some info
	    if(PopulationDensity.bootingIdlePlayersForLag != bootIdlePlayers ||
           PopulationDensity.grindersStopped != stopGrinders ||
           removeEntities)
	    {
	        String logEntry = "TPS: " + tps + " of 20.  Underperforming for " + minutesLagging + " minutes.  ";
	        
	        if(PopulationDensity.bootingIdlePlayersForLag != bootIdlePlayers)
	        {
	             if(bootIdlePlayers)
	                 logEntry += "Now booting idle players.  ";
	             else
	                 logEntry += "Allowing players to idle while there are slots open.  ";
	        }
	        
	        if(PopulationDensity.grindersStopped != stopGrinders)
	        {
	            if(stopGrinders)
	                logEntry += "Temporarily disabled monster grinders.  ";
	            else
	                logEntry += "Re-enabled monster grinders.  ";
	        }
	        
	        if(removeEntities)
	        {
	            logEntry += "Actively scanning to remove densely-packed entities...";
	        }
	        
	        PopulationDensity.AddLogEntry(logEntry);
	    }
	    
	    PopulationDensity.bootingIdlePlayersForLag = bootIdlePlayers;
	    PopulationDensity.grindersStopped = stopGrinders;
	    
	    if(removeEntities)
	    {
	        thinEntities();
	    }
	    
	    PopulationDensity.serverTicksPerSecond = tps;
	}
    
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
        for(World world : PopulationDensity.instance.getServer().getWorlds())
        {
            for(Chunk chunk : world.getLoadedChunks())
            {
                HashMap<String, Integer> entityCounter = new HashMap<String, Integer>();
                
                Entity [] entities = chunk.getEntities();
                int monsterCount = 0;
                for(Entity entity : entities)
                {
                    EntityType entityType = entity.getType();
                    Integer count = entityCounter.get(entity.getType());
                    if(count == null) count = 0;
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
                    entityCounter.put(entityTypeID, count + 1);
                    
                    if(entity instanceof LivingEntity) totalEntities++;
                    
                    //skip any entities with nameplates
                    if(entity.getCustomName() != null && entity.getCustomName() != "") continue;
                    
                    EntityType type = entity.getType();
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
                    else if(entity instanceof Monster)
                    {
                        if(++monsterCount > 3)
                        {
                            entity.remove();
                            totalRemoved++;
                        }
                    }
                    else if(entity instanceof Animals)
                    {
                        if(count > 20 || (count > 5 && count % 5 == 1))
                        {
                            //only specific types of animals may be removed
                            if(!thinnableAnimals.contains(type)) continue;
                            
                            //skip any pets
                            if(entity instanceof Tameable)
                            {
                                if(((Tameable)entity).isTamed()) continue;
                            }
                            
                            ((Animals) entity).setHealth(0);
                            totalRemoved++;
                        }
                    }
                }
            }
        }
        
        PopulationDensity.AddLogEntry("Removed " + totalRemoved + " of " + totalEntities + " entities.");
    }
}
