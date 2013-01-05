package me.ryanhamshire.PopulationDensity;

import java.util.ArrayList;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.bukkit.ChunkSnapshot;
import org.bukkit.Material;

public class ScanRegionTask implements Runnable 
{
	private ChunkSnapshot[][] chunks;
	private boolean openNewRegions;
	
	private final int CHUNK_SIZE = 16;

	public ScanRegionTask(ChunkSnapshot chunks[][], boolean openNewRegions)
	{
		this.chunks = chunks;
		this.openNewRegions = openNewRegions;
	}
	
	private class Position
	{
		public int x;
		public int y;
		public int z;
		
		public Position(int x, int y, int z)
		{
			this.x = x;
			this.y = y;
			this.z = z;
		}
		
		public String toString()
		{
			return this.x + " " + this.y + " " + this.z;					
		}
	}
	
	@Override
	public void run() 
	{
		ArrayList<String> logEntries = new ArrayList<String>();
		
		//initialize report content
		int woodCount = 0;
		int coalCount = 0;
		int ironCount = 0;
		int goldCount = 0;
		int redstoneCount = 0;
		int diamondCount = 0;
		int playerBlocks = 0;

		//initialize a new array to track where we've been
		int maxHeight = PopulationDensity.ManagedWorld.getMaxHeight();
		int x, y, z;
		x = y = z = 0;
		boolean [][][] examined = new boolean [this.chunks.length * CHUNK_SIZE][maxHeight][this.chunks.length * CHUNK_SIZE];
		for(x = 0; x < examined.length; x++)
			for(y = 0; y < examined[0].length; y++)
				for(z = 0; z < examined[0][0].length; z++)
					examined[x][y][z] = false;
		
		//find a reasonable start position
		Position currentPosition = null;
		for(x = 0; x < examined.length && currentPosition == null; x++)
		{
			for(z = 0; z < examined[0][0].length && currentPosition == null; z++)
			{
				Position position = new Position(x, maxHeight - 1, z);
				if(this.getMaterialAt(position) == Material.AIR)
				{
					currentPosition = position;
				}
			}
		}
		
		//set depth boundary
		//if a player has to brave cavernous depths, those resources aren't "easily attainable"
		int min_y = PopulationDensity.instance.minimumRegionPostY - 20;
		
		//instantiate empty queue
		ConcurrentLinkedQueue<Position> unexaminedQueue = new ConcurrentLinkedQueue<Position>();
		
		//mark start position as examined
		try
		{
			examined[currentPosition.x][currentPosition.y][currentPosition.z] = true;				
		}
		catch(ArrayIndexOutOfBoundsException e)
		{
			logEntries.add("Unexpected Exception: " + e.toString());
		}
		
		//enqueue that start position
		unexaminedQueue.add(currentPosition);	
				
		//as long as there are positions in the queue, keep going
		while(!unexaminedQueue.isEmpty())
		{
			//dequeue a block
			currentPosition = unexaminedQueue.remove();
			
			//get material
			Material material = this.getMaterialAt(currentPosition);
			
			//material == null indicates the data is out of bounds (not in the snapshots)
			//in that case, just move on to the next item in the queue
			if(material == null || currentPosition.y < min_y) continue;
			
			//if it's not a pass-through block
			if(		material != Material.AIR && 
					material != Material.WOOD_DOOR && 
					material != Material.WOODEN_DOOR &&
					material != Material.IRON_DOOR_BLOCK && 
					material != Material.TRAP_DOOR &&
					material != Material.LADDER
					)
			{
				//if it's a valuable resource, count it
				if(material == Material.LOG) woodCount++;
				else if (material == Material.COAL_ORE) coalCount++;
				else if (material == Material.IRON_ORE) ironCount++;
				else if (material == Material.GOLD_ORE) goldCount++;
				else if (material == Material.REDSTONE_ORE) redstoneCount++;
				else if (material == Material.DIAMOND_ORE) diamondCount++;	
				
				//if it's a player block, count it
				else if (
						material != Material.WATER && 
						material != Material.STATIONARY_LAVA &&
						material != Material.STATIONARY_WATER &&
						material != Material.BROWN_MUSHROOM && 
						material != Material.CACTUS &&
						material != Material.DEAD_BUSH && 
						material != Material.DIRT &&
						material != Material.GRAVEL &&
						material != Material.GRASS &&
						material != Material.HUGE_MUSHROOM_1 &&
						material != Material.HUGE_MUSHROOM_2 &&
						material != Material.ICE &&
						material != Material.LAPIS_ORE &&
						material != Material.LAVA &&
						material != Material.OBSIDIAN &&
						material != Material.RED_MUSHROOM &&
						material != Material.RED_ROSE &&
						material != Material.LEAVES &&
						material != Material.LOG &&
						material != Material.LONG_GRASS &&
						material != Material.SAND &&
						material != Material.SANDSTONE &&
						material != Material.SNOW &&
						material != Material.STONE &&
						material != Material.VINE &&
						material != Material.WATER_LILY &&
						material != Material.YELLOW_FLOWER &&
						material != Material.MOSSY_COBBLESTONE && 
						material != Material.CLAY &&
						material != Material.SUGAR_CANE_BLOCK)
				{
					playerBlocks++;
				}
			}
			
			//otherwise for pass-through blocks, enqueue the blocks around them for examination
			else
			{
				//make a list of adjacent blocks
				ConcurrentLinkedQueue<Position> adjacentPositionQueue = new ConcurrentLinkedQueue<Position>();
									
				//x + 1
				adjacentPositionQueue.add(new Position(currentPosition.x + 1, currentPosition.y, currentPosition.z));
				
				//x - 1
				adjacentPositionQueue.add(new Position(currentPosition.x - 1, currentPosition.y, currentPosition.z));
				
				//z + 1
				adjacentPositionQueue.add(new Position(currentPosition.x, currentPosition.y, currentPosition.z + 1));
				
				//z - 1
				adjacentPositionQueue.add(new Position(currentPosition.x, currentPosition.y, currentPosition.z - 1));
				
				//y + 1
				adjacentPositionQueue.add(new Position(currentPosition.x, currentPosition.y + 1, currentPosition.z));
				
				//y - 1
				adjacentPositionQueue.add(new Position(currentPosition.x, currentPosition.y - 1, currentPosition.z));
										
				//for each adjacent block
				while(!adjacentPositionQueue.isEmpty())
				{
					Position adjacentPosition = adjacentPositionQueue.remove();
					
					try
					{
						//if it hasn't been examined yet
						if(!examined[adjacentPosition.x][adjacentPosition.y][adjacentPosition.z])
						{					
							//mark it as examined
							examined[adjacentPosition.x][adjacentPosition.y][adjacentPosition.z] = true;
							
							//shove it in the queue for processing
							unexaminedQueue.add(adjacentPosition);
						}
					}
					
					//ignore any adjacent blocks which are outside the snapshots
					catch(ArrayIndexOutOfBoundsException e){ }
				}					
			}
		}			
		
		//compute a resource score
		int resourceScore = coalCount * 2 + ironCount * 3 + goldCount * 3 + redstoneCount * 3 + diamondCount * 4;
		
		//due to a race condition, bukkit might say a chunk is loaded when it really isn't.
		//in that case, bukkit will incorrectly report that all of the blocks in the chunk are air
		//strategy: if resource score and wood count are flat zero, the result is suspicious, so wait 5 seconds for chunks to load and start over
		//to avoid an infinite loop in a resource-bare region, maximum ONE repetition
		
		//deliver report
		logEntries.add("");								
		logEntries.add("Region Scan Results :");
		logEntries.add("");				
		logEntries.add("         Wood :" + woodCount + "  (Minimum: 200)");
		logEntries.add("         Coal :" + coalCount);
		logEntries.add("         Iron :" + ironCount);
		logEntries.add("         Gold :" + goldCount);
		logEntries.add("     Redstone :" + redstoneCount);
		logEntries.add("      Diamond :" + diamondCount);
		logEntries.add("Player Blocks :" + playerBlocks + "  (Maximum: " + (PopulationDensity.instance.densityRatio * 15000) + ")");
		logEntries.add("");
		logEntries.add(" Resource Score : " + resourceScore + "  (Minimum: 200)");
		logEntries.add("");								
		
		//if NOT sufficient resources for a good start
		if(resourceScore < 200 || woodCount < 200 || playerBlocks > 15000 * PopulationDensity.instance.densityRatio)
		{					
			if(resourceScore < 200 || woodCount < 200)
			{
				logEntries.add("Summary: Insufficient near-surface resources to support new players.");			
			}
			else if(playerBlocks > 15000 * PopulationDensity.instance.densityRatio)
			{
				logEntries.add("Summary: Region seems overcrowded.");			
			}
		}
		
		//otherwise
		else
		{
			logEntries.add("Summary: Looks good!  This region is suitable for new players.");
			openNewRegions = false;
		}
		
		//now that we're done, notify the main thread
		ScanResultsTask resultsTask = new ScanResultsTask(logEntries, openNewRegions);
		PopulationDensity.instance.getServer().getScheduler().scheduleSyncDelayedTask(PopulationDensity.instance, resultsTask, 5L);
	}
	
	private Material getMaterialAt(Position position)
	{
		Material material = null;
		
		int chunkx = position.x / 16;
		int chunkz = position.z / 16;
		
		try
		{
			ChunkSnapshot snapshot = this.chunks[chunkx][chunkz];
			int materialID = snapshot.getBlockTypeId(position.x % 16, position.y, position.z % 16);
			material = Material.getMaterial(materialID);
		}
		catch(IndexOutOfBoundsException e) { }
		
		return material;
	}	
}
