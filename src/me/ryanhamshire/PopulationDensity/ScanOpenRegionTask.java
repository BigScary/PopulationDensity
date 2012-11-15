package me.ryanhamshire.PopulationDensity;

import org.bukkit.Chunk;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;

public class ScanOpenRegionTask implements Runnable 
{
	@Override
	public void run() 
	{
		//scan loaded chunks for chunks with too many monsters or items, and remove the superfluous
		Chunk [] chunks = PopulationDensity.ManagedWorld.getLoadedChunks();
		for(int i = 0; i < chunks.length; i++)
		{
			Chunk chunk = chunks[i];
			
			Entity [] entities = chunk.getEntities();
			
			int monsterCount = 0;
			int itemCount = 0;		
			int animalCount = 0;
			
			for(int j = 0; j < entities.length; j++)
			{
				Entity entity = entities[j];
				
				if(entity instanceof Animals)
				{
					animalCount++;
					if(animalCount > 8)
					{
						entity.remove();
					}
				}
				
				else if(entity instanceof Creature)
				{
					monsterCount++;
					if(monsterCount > 5)
					{
						entity.remove();
					}
				}
				
				else if(entity instanceof Item)
				{
					itemCount++;
					if(itemCount > 25)
					{
						entity.remove();
					}
				}
			}
		}
		
		//start a scan on the currently open region
		PopulationDensity.instance.scanRegion(PopulationDensity.instance.dataStore.getOpenRegion(), true);		
	}	
}
