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

import java.io.*;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import com.google.common.io.Files;

public class DataStore 
{
	//in-memory cache for player home region, because it's needed very frequently
	private HashMap<String, PlayerData> playerNameToPlayerDataMap = new HashMap<String, PlayerData>();
	
	//path information, for where stuff stored on disk is well...  stored
	private final static String dataLayerFolderPath = "plugins" + File.separator + "PopulationDensityData";
	private final static String playerDataFolderPath = dataLayerFolderPath + File.separator + "PlayerData";
	private final static String regionDataFolderPath = dataLayerFolderPath + File.separator + "RegionData";
	public final static String configFilePath = dataLayerFolderPath + File.separator + "config.yml";
	final static String messagesFilePath = dataLayerFolderPath + File.separator + "messages.yml";
	
    //in-memory cache for messages
    private String [] messages;
	
	//currently open region
	private RegionCoordinates openRegionCoordinates;
	
	//coordinates of the next region which will be opened, if one needs to be opened
	private RegionCoordinates nextRegionCoordinates;
	
	//region data cache
	private ConcurrentHashMap<String, RegionCoordinates> nameToCoordsMap = new ConcurrentHashMap<String, RegionCoordinates>();
	private ConcurrentHashMap<RegionCoordinates, String> coordsToNameMap = new ConcurrentHashMap<RegionCoordinates, String>();
	
	//initialization!
	public DataStore()
	{
		//ensure data folders exist
		new File(playerDataFolderPath).mkdirs();
		new File(regionDataFolderPath).mkdirs();
		
		this.loadMessages();
		
		//get a list of all the files in the region data folder
        //some of them are named after region names, others region coordinates
        File regionDataFolder = new File(regionDataFolderPath);
        File [] files = regionDataFolder.listFiles();           
        
        for(int i = 0; i < files.length; i++)
        {               
            if(files[i].isFile())  //avoid any folders
            {
                try
                {
                    //if the filename converts to region coordinates, add that region to the list of defined regions
                    //(this constructor throws an exception if it can't do the conversion)
                    RegionCoordinates regionCoordinates = new RegionCoordinates(files[i].getName());
                    String regionName = Files.readFirstLine(files[i], Charset.forName("UTF-8"));
                    this.nameToCoordsMap.put(regionName, regionCoordinates);
                    this.coordsToNameMap.put(regionCoordinates, regionName);
                }
                
                //catch for files named after region names
                catch(Exception e){ }                   
            }
        }
		
		//study region data and initialize both this.openRegionCoordinates and this.nextRegionCoordinates
		this.findNextRegion();
		
		//if no regions were loaded, create the first one
		if(nameToCoordsMap.keySet().size() == 0)
		{
			PopulationDensity.AddLogEntry("Please be patient while I search for a good new player starting point!");
			PopulationDensity.AddLogEntry("This initial scan could take a while, especially for worlds where players have already been building.");
			this.addRegion();			
		}
		
		PopulationDensity.AddLogEntry("Open region: \"" + this.getRegionName(this.getOpenRegion()) + "\" at " + this.getOpenRegion().toString() + ".");		
	}
	
	//used in the spiraling code below (see findNextRegion())
	private enum Direction { left, right, up, down }	
	
	//starts at region 0,0 and spirals outward until it finds a region which hasn't been initialized
	//sets private variables for openRegion and nextRegion when it's done
	//this may look like black magic, but seriously, it produces a tight spiral on a grid
	//coding this made me reminisce about seemingly pointless computer science exercises in college
	public int findNextRegion()
	{
		//spiral out from region coordinates 0, 0 until we find coordinates for an uninitialized region
		int x = 0; int z = 0;
		
		//keep count of the regions encountered
		int regionCount = 0;

		//initialization
		Direction direction = Direction.down;   //direction to search
		int sideLength = 1;  					//maximum number of regions to move in this direction before changing directions
		int side = 0;        					//increments each time we change directions.  this tells us when to add length to each side
		this.openRegionCoordinates = new RegionCoordinates(0, 0);
		this.nextRegionCoordinates = new RegionCoordinates(0, 0);

		//while the next region coordinates are taken, walk the spiral
		while (this.getRegionName(this.nextRegionCoordinates) != null)
		{
			//loop for one side of the spiral
			for (int i = 0; i < sideLength && this.getRegionName(this.nextRegionCoordinates) != null; i++)
			{
				regionCount++;
				
				//converts a direction to a change in X or Z
				if (direction == Direction.down) z++;
				else if (direction == Direction.left) x--;
				else if (direction == Direction.up) z--;
				else x++;
				
				this.openRegionCoordinates = this.nextRegionCoordinates;
				this.nextRegionCoordinates = new RegionCoordinates(x, z);
			}
		
			//after finishing a side, change directions
			if (direction == Direction.down) direction = Direction.left;
			else if (direction == Direction.left) direction = Direction.up;
			else if (direction == Direction.up) direction = Direction.right;
			else direction = Direction.down;
			
			//keep count of the completed sides
			side++;

			//on even-numbered sides starting with side == 2, increase the length of each side
			if (side % 2 == 0) sideLength++;
		}
		
		//return total number of regions seen
		return regionCount;
	}
	
	//picks a region at random (sort of)
	public RegionCoordinates getRandomRegion(RegionCoordinates regionToAvoid)
	{
		if(this.coordsToNameMap.keySet().size() < 2) return null;
		
		//initialize random number generator with a seed based the current time
		Random randomGenerator = new Random();
		
		ArrayList<RegionCoordinates> possibleDestinations = new ArrayList<RegionCoordinates>();
		for(RegionCoordinates coords : this.coordsToNameMap.keySet())
		{
		    if(!coords.equals(regionToAvoid))
		    {
		        possibleDestinations.add(coords);
		    }
		}
		
		//pick one of those regions at random
		int randomRegion = randomGenerator.nextInt(possibleDestinations.size());			
		return possibleDestinations.get(randomRegion);			
	}
	
	public void savePlayerData(OfflinePlayer player, PlayerData data)
	{
		//save that data in memory
		this.playerNameToPlayerDataMap.put(player.getUniqueId().toString(), data);
		
		BufferedWriter outStream = null;
		try
		{
			//open the player's file
			File playerFile = new File(playerDataFolderPath + File.separator + player.getUniqueId().toString());
			playerFile.createNewFile();
			outStream = new BufferedWriter(new FileWriter(playerFile));
			
			//first line is home region coordinates
			outStream.write(data.homeRegion.toString());
			outStream.newLine();
			
			//second line is last disconnection date,
			//note use of the ROOT locale to avoid problems related to regional settings on the server being updated
			DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.FULL, Locale.ROOT);			
			outStream.write(dateFormat.format(data.lastDisconnect));
			outStream.newLine();
			
			//third line is login priority
			outStream.write(String.valueOf(data.loginPriority));
			outStream.newLine();
		}		
		
		//if any problem, log it
		catch(Exception e)
		{
			PopulationDensity.AddLogEntry("PopulationDensity: Unexpected exception saving data for player \"" + player.getName() + "\": " + e.getMessage());
		}		
		
		try
		{
			//close the file
			if(outStream != null) outStream.close();
		}
		catch(IOException exception){}
	}
	
	public PlayerData getPlayerData(OfflinePlayer player)
	{
		//first, check the in-memory cache
		PlayerData data = this.playerNameToPlayerDataMap.get(player.getUniqueId().toString());

		if(data != null) return data;
		
		//if not there, try to load the player from file using UUID		
		loadPlayerDataFromFile(player.getUniqueId().toString(), player.getUniqueId().toString());

		//check again
		data = this.playerNameToPlayerDataMap.get(player.getUniqueId().toString());
		
		if(data != null) return data;

		//if still not there, try player name
		loadPlayerDataFromFile(player.getName(), player.getUniqueId().toString());
		
		//check again
        data = this.playerNameToPlayerDataMap.get(player.getUniqueId().toString());
        
        if(data != null) return data;

        return new PlayerData();
	}
	
	private void loadPlayerDataFromFile(String source, String dest)
	{
		//load player data into memory		
		File playerFile = new File(playerDataFolderPath + File.separator + source);
		
		BufferedReader inStream = null;
		try
		{					
			PlayerData playerData = new PlayerData();
			inStream = new BufferedReader(new FileReader(playerFile.getAbsolutePath()));
						
			//first line is home region coordinates
			String homeRegionCoordinatesString = inStream.readLine();
			
			//second line is date of last disconnection
			String lastDisconnectedString = inStream.readLine();
			
			//third line is login priority
			String rankString = inStream.readLine(); 
			
			//convert string representation of home coordinates to a proper object
			RegionCoordinates homeRegionCoordinates = new RegionCoordinates(homeRegionCoordinatesString);
			playerData.homeRegion = homeRegionCoordinates;
			  
			//parse the last disconnect date string
			try
			{
				DateFormat dateFormat = DateFormat.getDateTimeInstance(DateFormat.FULL, DateFormat.FULL, Locale.ROOT);
				Date lastDisconnect = dateFormat.parse(lastDisconnectedString);
				playerData.lastDisconnect = lastDisconnect;
			}
			catch(Exception e)
			{
				playerData.lastDisconnect = Calendar.getInstance().getTime();
			}
			
			//parse priority string
			if(rankString == null || rankString.isEmpty())
			{
				playerData.loginPriority = 0;
			}
			
			else
			{
				try
				{
					playerData.loginPriority = Integer.parseInt(rankString);
				}
				catch(Exception e)
				{
					playerData.loginPriority = 0;
				}			
			}
			  
			//shove into memory for quick access
			this.playerNameToPlayerDataMap.put(dest, playerData);
		}
		
		//if the file isn't found, just don't do anything (probably a new-to-server player)
		catch(FileNotFoundException e) 
		{ 
			return;
		}
		
		//if there's any problem with the file's content, log an error message and skip it		
		catch(Exception e)
		{
			 PopulationDensity.AddLogEntry("Unable to load data for player \"" + source + "\": " + e.getMessage());			 
		}
		
		try
		{
			if(inStream != null) inStream.close();
		}
		catch(IOException exception){}		
	}
	
	//adds a new region, assigning it a name and updating local variables accordingly
	public RegionCoordinates addRegion()
	{
		//first, find a unique name for the new region
		String newRegionName; 
		
		//select a name from the list of region names		
		//strategy: use names from the list in rotation, appending a number when a name is already used
		//(redstone, mountain, valley, redstone1, mountain1, valley1, ...)
		
		int newRegionNumber = this.coordsToNameMap.keySet().size() - 1;
		
		//as long as the generated name is already in use, move up one name on the list
		do
		{
			newRegionNumber++;
			int nameBodyIndex = newRegionNumber % this.regionNamesList.length;
			int nameSuffix = newRegionNumber / this.regionNamesList.length;
			newRegionName = this.regionNamesList[nameBodyIndex];
			if(nameSuffix > 0) newRegionName += nameSuffix;
			
		}while(this.getRegionCoordinates(newRegionName) != null);
		
		this.nameRegion(this.nextRegionCoordinates, newRegionName);		
		
		//find the next region in the spiral (updates this.openRegionCoordinates and this.nextRegionCoordinates)
		this.findNextRegion();
		
		return this.openRegionCoordinates;
	}
	
	void nameRegion(RegionCoordinates coords, String name) 
	{
		//region names are always lowercase
		name = name.toLowerCase();
		
		//delete any existing data for the region at these coordinates
		String oldRegionName = this.getRegionName(coords);
		if(oldRegionName != null)
		{
			File oldRegionCoordinatesFile = new File(regionDataFolderPath + File.separator + coords.toString());
			oldRegionCoordinatesFile.delete();
			
			File oldRegionNameFile = new File(regionDataFolderPath + File.separator + oldRegionName);
			oldRegionNameFile.delete();
		}

		//"create" the region by saving necessary data to disk
		BufferedWriter outStream = null;
		try
		{
			//coordinates file contains the region's name
            File regionCoordinatesFile = new File(regionDataFolderPath + File.separator + coords.toString());
            regionCoordinatesFile.createNewFile();
            outStream = new BufferedWriter(new FileWriter(regionCoordinatesFile));
            outStream.write(name);
            outStream.close();
			
			//cache in memory
			this.coordsToNameMap.put(coords, name);
			this.nameToCoordsMap.put(name, coords);
		}
		
		//in case of any problem, log the details
		catch(Exception e)
		{
			PopulationDensity.AddLogEntry("Unexpected Exception: " + e.getMessage());
		}
		
		try
		{
			if(outStream != null) outStream.close();		
		}
		catch(IOException exception){}
	}

	//retrieves the open region's coordinates
	public RegionCoordinates getOpenRegion()
	{
		return this.openRegionCoordinates;
	}
	
	//goes to disk to get the name of a region, given its coordinates
	public String getRegionName(RegionCoordinates coordinates)
	{
		return this.coordsToNameMap.get(coordinates);
	}
	
	//similar to above, goes to disk to get the coordinates that go with a region name
	public RegionCoordinates getRegionCoordinates(String regionName)
	{
		return this.nameToCoordsMap.get(regionName);
	}
	
	//actually edits the world to create a region post at the center of the specified region	
	public void AddRegionPost(RegionCoordinates region, boolean updateNeighboringRegions)
	{
		//if region post building is disabled, don't do anything
		if(!PopulationDensity.instance.buildRegionPosts) return;
		
		//find the center
		Location regionCenter = PopulationDensity.getRegionCenter(region);		
		int x = regionCenter.getBlockX();
		int z = regionCenter.getBlockZ();
		int y;

		//make sure data is loaded for that area, because we're about to request data about specific blocks there
		PopulationDensity.GuaranteeChunkLoaded(x, z);
		
		//sink lower until we find something solid
		//also ignore glowstone, in case there's already a post here!
		//race condition issue: chunks say they're loaded when they're not.  if it looks like the chunk isn't loaded, try again (up to five times)
		int retriesLeft = 5;
		boolean tryAgain;
		Material blockType;
		do
		{
			tryAgain = false;
			
			//find the highest block.  could be the surface, a tree, some grass...
			y = PopulationDensity.ManagedWorld.getHighestBlockYAt(x, z) + 1;
			
			//posts fall through trees, snow, and any existing post looking for the ground
			do
			{
				blockType = PopulationDensity.ManagedWorld.getBlockAt(x, --y, z).getType();
			}
			while(	y > 2 && (
					blockType == Material.AIR 		|| 
					blockType == Material.LEAVES 	|| 
			        blockType == Material.LEAVES_2  ||
					blockType == Material.LONG_GRASS||
					blockType == Material.LOG       ||
			        blockType == Material.LOG_2     ||
					blockType == Material.SNOW 		||
					blockType == Material.VINE					
					));
			
			//if final y value is extremely small, it's probably wrong
			if(y < 5 && retriesLeft-- > 0)
			{
				tryAgain = true;
				try
				{
					Thread.sleep(500); //sleep half a second before restarting the loop
				}
				catch(InterruptedException e) {}
			}
		}while(tryAgain);
				
		if(blockType == Material.SIGN_POST)
		{
		    y -= 4;
		}
		else if(blockType == Material.GLOWSTONE || (blockType == Material.getMaterial(PopulationDensity.instance.postTopperId)))
		{
		    y -= 3;
		}
		
		//if y value is under sea level, correct it to sea level (no posts should be that difficult to find)
		if(y < PopulationDensity.instance.minimumRegionPostY)
		{
			y = PopulationDensity.instance.minimumRegionPostY;
		}
		
		//clear signs from the area, this ensures signs don't drop as items 
		//when the blocks they're attached to are destroyed in the next step
		for(int x1 = x - 2; x1 <= x + 2; x1++)
		{
			for(int z1 = z - 2; z1 <= z + 2; z1++)
			{
				for(int y1 = y + 1; y1 <= y + 5; y1++)
				{
					Block block = PopulationDensity.ManagedWorld.getBlockAt(x1, y1, z1);
					if(block.getType() == Material.SIGN_POST || block.getType() == Material.SIGN || block.getType() == Material.WALL_SIGN)
						block.setType(Material.AIR);					
				}
			}
		}
		
		//clear above it - sometimes this shears trees in half (doh!)
		for(int x1 = x - 2; x1 <= x + 2; x1++)
		{
			for(int z1 = z - 2; z1 <= z + 2; z1++)
			{
				for(int y1 = y + 1; y1 < PopulationDensity.ManagedWorld.getMaxHeight(); y1++)
				{
					PopulationDensity.ManagedWorld.getBlockAt(x1, y1, z1).setType(Material.AIR);
				}
			}
		}	
		
		//build top block
        PopulationDensity.ManagedWorld.getBlockAt(x, y + 3, z).setTypeIdAndData(PopulationDensity.instance.postTopperId, PopulationDensity.instance.postTopperData.byteValue(), true);
		
		//build outer platform
		for(int x1 = x - 2; x1 <= x + 2; x1++)
		{
			for(int z1 = z - 2; z1 <= z + 2; z1++)
			{
				PopulationDensity.ManagedWorld.getBlockAt(x1, y, z1).setTypeIdAndData(PopulationDensity.instance.outerPlatformId, PopulationDensity.instance.outerPlatformData.byteValue(), true);
			}
		}
		
		//build inner platform
        for(int x1 = x - 1; x1 <= x + 1; x1++)
        {
            for(int z1 = z - 1; z1 <= z + 1; z1++)
            {
                PopulationDensity.ManagedWorld.getBlockAt(x1, y, z1).setTypeIdAndData(PopulationDensity.instance.innerPlatformId, PopulationDensity.instance.innerPlatformData.byteValue(), true);
            }
        }
        
        //build lower center blocks
        for(int y1 = y; y1 <= y + 2; y1++)
        {
            PopulationDensity.ManagedWorld.getBlockAt(x, y1, z).setTypeIdAndData(PopulationDensity.instance.postId, PopulationDensity.instance.postData.byteValue(), true);
        }
		
		//build a sign on top with region name (or wilderness if no name)
		String regionName = this.getRegionName(region);
		if(regionName == null) regionName = "Wilderness";
		regionName = PopulationDensity.capitalize(regionName);
		Block block = PopulationDensity.ManagedWorld.getBlockAt(x, y + 4, z);
		block.setType(Material.SIGN_POST);
		
		org.bukkit.block.Sign sign = (org.bukkit.block.Sign)block.getState();
		sign.setLine(1, PopulationDensity.capitalize(regionName));
		sign.setLine(2, "Region");
		sign.update();
		
		//add a sign for the region to the south
		regionName = this.getRegionName(new RegionCoordinates(region.x + 1, region.z));
		if(regionName == null) regionName = "Wilderness";
		regionName = PopulationDensity.capitalize(regionName);
		
		block = PopulationDensity.ManagedWorld.getBlockAt(x, y + 2, z - 1);
		
		org.bukkit.material.Sign signData = new org.bukkit.material.Sign(Material.WALL_SIGN);
		signData.setFacingDirection(BlockFace.NORTH);
		
		block.setTypeIdAndData(Material.WALL_SIGN.getId(), signData.getData(), false);
		
		sign = (org.bukkit.block.Sign)block.getState();
		
		sign.setLine(0, "<--");
		sign.setLine(1, regionName);
	    sign.setLine(2, "Region");
		sign.setLine(3, "<--");
		
		sign.update();
		
		//if a city world is defined, also add a /cityregion sign on the east side of the post
		if(PopulationDensity.CityWorld != null)
		{
			block = PopulationDensity.ManagedWorld.getBlockAt(x, y + 3, z - 1);
			
			//signData = new org.bukkit.material.Sign(Material.WALL_SIGN);
			//signData.setFacingDirection(BlockFace.NORTH);
			
			//block.setTypeIdAndData(Material.WALL_SIGN.getId(), signData.getData(), false);
			
			//sign = (org.bukkit.block.Sign)block.getState();
			
			//sign.update();
		}
		
		//add a sign for the region to the east
		regionName = this.getRegionName(new RegionCoordinates(region.x, region.z - 1));
		if(regionName == null) regionName = "Wilderness";
		regionName = PopulationDensity.capitalize(regionName);
		
		block = PopulationDensity.ManagedWorld.getBlockAt(x - 1, y + 2, z);
		
		signData = new org.bukkit.material.Sign(Material.WALL_SIGN);
		signData.setFacingDirection(BlockFace.WEST);
		
		block.setTypeIdAndData(Material.WALL_SIGN.getId(), signData.getData(), false);
		
		sign = (org.bukkit.block.Sign)block.getState();
		
		sign.setLine(0, "<--");
        sign.setLine(1, regionName);
        sign.setLine(2, "Region");
        sign.setLine(3, "<--");
		
		sign.update();
		
		//if teleportation is enabled, also add a sign facing north for teleportation help
		if(PopulationDensity.instance.allowTeleportation)
		{
			block = PopulationDensity.ManagedWorld.getBlockAt(x - 1, y + 3, z);
			
			signData = new org.bukkit.material.Sign(Material.WALL_SIGN);
			signData.setFacingDirection(BlockFace.WEST);
			
			block.setTypeIdAndData(Material.WALL_SIGN.getId(), signData.getData(), false);
			
			sign = (org.bukkit.block.Sign)block.getState();
			
			sign.setLine(0, "Teleport");
			sign.setLine(1, "From Here!");
			sign.setLine(2, "Punch For");
			sign.setLine(3, "Instructions");
			
			sign.update();
		}
		
		//add a sign for the region to the south
		regionName = this.getRegionName(new RegionCoordinates(region.x, region.z + 1));
		if(regionName == null) regionName = "Wilderness";
		regionName = PopulationDensity.capitalize(regionName);
		
		block = PopulationDensity.ManagedWorld.getBlockAt(x + 1, y + 2, z);
		
		signData = new org.bukkit.material.Sign(Material.WALL_SIGN);
		signData.setFacingDirection(BlockFace.EAST);
		
		block.setTypeIdAndData(Material.WALL_SIGN.getId(), signData.getData(), false);
		
		sign = (org.bukkit.block.Sign)block.getState();
		
		sign.setLine(0, "<--");
        sign.setLine(1, regionName);
        sign.setLine(2, "Region");
        sign.setLine(3, "<--");
		
		sign.update();
		
		//if teleportation is enabled, also add a sign facing south for teleportation help
		if(PopulationDensity.instance.allowTeleportation)
		{
			block = PopulationDensity.ManagedWorld.getBlockAt(x + 1, y + 3, z);
			signData = new org.bukkit.material.Sign(Material.WALL_SIGN);
			signData.setFacingDirection(BlockFace.EAST);
			
			block.setTypeIdAndData(Material.WALL_SIGN.getId(), signData.getData(), false);
			
			sign = (org.bukkit.block.Sign)block.getState();
			
			sign.setLine(0, "Teleport");
            sign.setLine(1, "From Here!");
            sign.setLine(2, "Punch For");
            sign.setLine(3, "Instructions");
			
			sign.update();
		}
		
		//add a sign for the region to the north
		regionName = this.getRegionName(new RegionCoordinates(region.x - 1, region.z));
		if(regionName == null) regionName = "Wilderness";
		regionName = PopulationDensity.capitalize(regionName);
		
		block = PopulationDensity.ManagedWorld.getBlockAt(x, y + 2, z + 1);
		
		signData = new org.bukkit.material.Sign(Material.WALL_SIGN);
		signData.setFacingDirection(BlockFace.SOUTH);
		
		block.setTypeIdAndData(Material.WALL_SIGN.getId(), signData.getData(), false);
		
		sign = (org.bukkit.block.Sign)block.getState();
		
		sign.setLine(0, "<--");
        sign.setLine(1, regionName);
        sign.setLine(2, "Region");
        sign.setLine(3, "<--");
		
		sign.update();
		
		//if teleportation is enabled, also add a sign facing west for /newestregion
		if(PopulationDensity.instance.allowTeleportation && !this.openRegionCoordinates.equals(region))
		{
			//block = PopulationDensity.ManagedWorld.getBlockAt(x, y + 3, z + 1);

			//signData = new org.bukkit.material.Sign(Material.WALL_SIGN);
			//signData.setFacingDirection(BlockFace.SOUTH);
			
			//block.setTypeIdAndData(Material.WALL_SIGN.getId(), signData.getData(), false);

			//sign = (org.bukkit.block.Sign)block.getState();
			
			//sign.update();
		}
		
		//custom signs
		
		if(PopulationDensity.instance.mainCustomSignContent != null)
		{
			block = PopulationDensity.ManagedWorld.getBlockAt(x, y + 3, z - 1);

			signData = new org.bukkit.material.Sign(Material.WALL_SIGN);
			signData.setFacingDirection(BlockFace.NORTH);
			
			block.setTypeIdAndData(Material.WALL_SIGN.getId(), signData.getData(), false);
			
			sign = (org.bukkit.block.Sign)block.getState();
			
			for(int i = 0; i < 4; i++)
			{
				sign.setLine(i, PopulationDensity.instance.mainCustomSignContent[i]);
			}
			
			sign.update();
		}
		
		if(PopulationDensity.instance.northCustomSignContent != null)
		{
			block = PopulationDensity.ManagedWorld.getBlockAt(x - 1, y + 1, z);

			signData = new org.bukkit.material.Sign(Material.WALL_SIGN);
			signData.setFacingDirection(BlockFace.WEST);
			
			block.setTypeIdAndData(Material.WALL_SIGN.getId(), signData.getData(), false);
			
			sign = (org.bukkit.block.Sign)block.getState();
			
			for(int i = 0; i < 4; i++)
			{
				sign.setLine(i, PopulationDensity.instance.northCustomSignContent[i]);
			}
			
			sign.update();
		}
		
		if(PopulationDensity.instance.southCustomSignContent != null)
		{
			block = PopulationDensity.ManagedWorld.getBlockAt(x + 1, y + 1, z);
			
			signData = new org.bukkit.material.Sign(Material.WALL_SIGN);
			signData.setFacingDirection(BlockFace.EAST);
			
			block.setTypeIdAndData(Material.WALL_SIGN.getId(), signData.getData(), false);
			
			sign = (org.bukkit.block.Sign)block.getState();
			
			for(int i = 0; i < 4; i++)
			{
				sign.setLine(i, PopulationDensity.instance.southCustomSignContent[i]);
			}
			
			sign.update();
		}
		
		if(PopulationDensity.instance.eastCustomSignContent != null)
		{
			block = PopulationDensity.ManagedWorld.getBlockAt(x, y + 1, z - 1);
			
			signData = new org.bukkit.material.Sign(Material.WALL_SIGN);
			signData.setFacingDirection(BlockFace.NORTH);
			
			block.setTypeIdAndData(Material.WALL_SIGN.getId(), signData.getData(), false);
			
			sign = (org.bukkit.block.Sign)block.getState();
			
			for(int i = 0; i < 4; i++)
			{
				sign.setLine(i, PopulationDensity.instance.eastCustomSignContent[i]);
			}
			
			sign.update();
		}
		
		if(PopulationDensity.instance.westCustomSignContent != null)
		{
			block = PopulationDensity.ManagedWorld.getBlockAt(x, y + 1, z + 1);
			
			signData = new org.bukkit.material.Sign(Material.WALL_SIGN);
			signData.setFacingDirection(BlockFace.SOUTH);
			
			block.setTypeIdAndData(Material.WALL_SIGN.getId(), signData.getData(), false);
			
			sign = (org.bukkit.block.Sign)block.getState();
			
			for(int i = 0; i < 4; i++)
			{
				sign.setLine(i, PopulationDensity.instance.westCustomSignContent[i]);
			}
			
			sign.update();
		}
		
		if(updateNeighboringRegions)
		{
			this.AddRegionPost(new RegionCoordinates(region.x - 1, region.z), false);
			this.AddRegionPost(new RegionCoordinates(region.x + 1, region.z), false);
			this.AddRegionPost(new RegionCoordinates(region.x, region.z - 1), false);
			this.AddRegionPost(new RegionCoordinates(region.x, region.z + 1), false);
		}
	}
	
	public void clearCachedPlayerData(Player player)
	{
		this.playerNameToPlayerDataMap.remove(player.getName());		
	}
	
	private void loadMessages() 
    {
        Messages [] messageIDs = Messages.values();
        this.messages = new String[Messages.values().length];
        
        HashMap<String, CustomizableMessage> defaults = new HashMap<String, CustomizableMessage>();
        
        //initialize defaults
        this.addDefault(defaults, Messages.NoManagedWorld, "The PopulationDensity plugin has not been properly configured.  Please update your config.yml to specify a world to manage.", null);
        this.addDefault(defaults, Messages.NoBreakPost, "You can't break blocks this close to the region post.", null);
        this.addDefault(defaults, Messages.NoBreakSpawn, "You can't break blocks this close to a player spawn point.", null);
        this.addDefault(defaults, Messages.NoBuildPost, "You can't place blocks this close to the region post.", null);
        this.addDefault(defaults, Messages.NoBuildSpawn, "You can't place blocks this close to a player spawn point.", null);
        this.addDefault(defaults, Messages.HelpMessage, "Region post help and commands: ", null);
        this.addDefault(defaults, Messages.BuildingAwayFromHome, "You're building outside of your home region.  If you'd like to make this region your new home to help you return here later, use /MoveIn.", null);
        this.addDefault(defaults, Messages.NoTeleportThisWorld, "You can't teleport from this world.", null);
        this.addDefault(defaults, Messages.OnlyHomeCityHere, "You're limited to /HomeRegion and /CityRegion here.", null);
        this.addDefault(defaults, Messages.NoTeleportHere, "Sorry, you can't teleport from here.", null);
        this.addDefault(defaults, Messages.NotCloseToPost, "You're not close enough to a region post to teleport.", null);
        this.addDefault(defaults, Messages.InvitationNeeded, "{0} lives in the wilderness.  He or she will have to /invite you.", "0: target player");
        this.addDefault(defaults, Messages.VisitConfirmation, "Teleported to {0}'s home region.", "0: target player");
        this.addDefault(defaults, Messages.DestinationNotFound, "There's no region or online player named \"{0}\".", "0: specified destination");
        this.addDefault(defaults, Messages.NeedNewestRegionPermission, "You don't have permission to use that command.", null);
        this.addDefault(defaults, Messages.NewestRegionConfirmation, "Teleported to the current new player area.", null);
        this.addDefault(defaults, Messages.NotInRegion, "You're not in a region!", null);
        this.addDefault(defaults, Messages.UnnamedRegion, "You're in the wilderness!  This region doesn't have a name.", null);
        this.addDefault(defaults, Messages.WhichRegion, "You're in the {0} region.", null);
        this.addDefault(defaults, Messages.RegionNamesNoSpaces, "Region names may not include spaces.", null);
        this.addDefault(defaults, Messages.RegionNamesTenLetters, "Region names must be at most 10 letters long.", null);
        this.addDefault(defaults, Messages.RegionNamesOnlyLetters, "Region names may only include letters.", null);
        this.addDefault(defaults, Messages.RegionNameConflict, "There's already a region by that name.", null);
        this.addDefault(defaults, Messages.NoMoreRegions, "Sorry, you're in the only region.  Over time, more regions will open.", null);
        this.addDefault(defaults, Messages.InviteConfirmation, "Invitation sent!  {0} can use /visit {1} to teleport to your home post.", "0: invitee's name, 1: inviter's name");
        this.addDefault(defaults, Messages.InviteNotification, "{0} has invited you to visit!", "0: inviter's name");
        this.addDefault(defaults, Messages.InviteInstruction, "Use /visit {0} to teleport there.", "0: inviter's name");
        this.addDefault(defaults, Messages.PlayerNotFound, "There's no player named \"{0}\" online right now.", "0: specified name");
        this.addDefault(defaults, Messages.SetHomeConfirmation, "Home set to the nearest region post!", null);
        this.addDefault(defaults, Messages.SetHomeInstruction1, "Use /Home from any region post to teleport to your home post.", null);
        this.addDefault(defaults, Messages.SetHomeInstruction2, "Use /Invite to invite other players to teleport to your home post.", null);
        this.addDefault(defaults, Messages.AddRegionConfirmation, "Opened a new region and started a resource scan.  See console or server logs for details.", null);
        this.addDefault(defaults, Messages.ScanStartConfirmation, "Started scan.  Check console or server logs for results.", null);
        this.addDefault(defaults, Messages.LoginPriorityCheck, "{0}'s login priority: {1}.", "0: player name, 1: current priority");
        this.addDefault(defaults, Messages.LoginPriorityUpdate, "Set {0}'s priority to {1}.", "0: target player, 1: new priority");
        this.addDefault(defaults, Messages.ThinningConfirmation, "Thinning running.  Check logs for detailed results.", null);
        this.addDefault(defaults, Messages.PerformanceScore, "Current server performance score is {0}%.", "0: performance score");
        this.addDefault(defaults, Messages.PerformanceScore_Lag, "  The server is actively working to reduce lag - please be patient while automatic lag reduction takes effect.", null);
        this.addDefault(defaults, Messages.PerformanceScore_NoLag, "The server is running at normal speed.  If you're experiencing lag, check your graphics settings and internet connection.  ", null);
        
        //load the config file
        FileConfiguration config = YamlConfiguration.loadConfiguration(new File(messagesFilePath));
        
        //for each message ID
        for(int i = 0; i < messageIDs.length; i++)
        {
            //get default for this message
            Messages messageID = messageIDs[i];
            CustomizableMessage messageData = defaults.get(messageID.name());
            
            //if default is missing, log an error and use some fake data for now so that the plugin can run
            if(messageData == null)
            {
                PopulationDensity.AddLogEntry("Missing message for " + messageID.name() + ".  Please contact the developer.");
                messageData = new CustomizableMessage(messageID, "Missing message!  ID: " + messageID.name() + ".  Please contact a server admin.", null);
            }
            
            //read the message from the file, use default if necessary
            this.messages[messageID.ordinal()] = config.getString("Messages." + messageID.name() + ".Text", messageData.text);
            config.set("Messages." + messageID.name() + ".Text", this.messages[messageID.ordinal()]);
            
            if(messageData.notes != null)
            {
                messageData.notes = config.getString("Messages." + messageID.name() + ".Notes", messageData.notes);
                config.set("Messages." + messageID.name() + ".Notes", messageData.notes);
            }
        }
        
        //save any changes
        try
        {
            config.save(DataStore.messagesFilePath);
        }
        catch(IOException exception)
        {
            PopulationDensity.AddLogEntry("Unable to write to the configuration file at \"" + DataStore.messagesFilePath + "\"");
        }
        
        defaults.clear();
        System.gc();                
    }

    private void addDefault(HashMap<String, CustomizableMessage> defaults, Messages id, String text, String notes)
    {
        CustomizableMessage message = new CustomizableMessage(id, text, notes);
        defaults.put(id.name(), message);       
    }

    synchronized public String getMessage(Messages messageID, String... args)
    {
        String message = messages[messageID.ordinal()];
        
        for(int i = 0; i < args.length; i++)
        {
            String param = args[i];
            message = message.replace("{" + i + "}", param);
        }
        
        return message;     
    }
	
	//list of region names to use
	private final String [] regionNamesList = new String [] 
	{
		"redstone",
		"morningthaw",
		"mountain",
		"valley",
		"wintersebb",
		"fjord",
		"ledge",
		"ravine",
		"darktide",
		"stream",
		"glenwood",
		"waterfall",
		"cragstone",
		"pickaxe",
		"axe",
		"hammer",
		"anvil",
		"field",
		"sunrise",
		"sunset",
		"copper",
		"coal",
		"shovel",
		"minecart",
		"railway",
		"tunnel",
		"chasm",
		"cavern",
		"ocean",
		"boat",
		"grass",
		"gust",
		"beach",
		"desert",
		"stone",
		"peak",
		"ore",
		"boulder",
		"hilltop",
		"horizon",
		"swamp",
		"molten",
		"canyon",
		"gravel",
		"sword",
		"obsidian",
		"treetop",
		"storm",
		"gold",
		"canopy",
		"forest",
		"summit",
		"glade",
		"trail",
		"seed",
		"diamond",
		"armor",
		"sand",
		"flint",
		"field",
		"steel",
		"helm",
		"gorge",
		"campfire",
		"workshop",
		"rubble",
		"iron",
		"torch",
		"moon",
		"shrub",
		"trunk",
		"garden",
		"vale",
		"pumpkin",
		"lantern",
		"charcoal",
		"marsh",
		"tundra",
		"taiga",
		"dust",
		"lava"
	};

    String getRegionNames()
    {
        StringBuilder builder = new StringBuilder();
        for(String regionName : this.nameToCoordsMap.keySet())
        {
            builder.append(regionName).append(' ');
        }
        
        return builder.toString();
    }
}
