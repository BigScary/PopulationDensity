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
import java.io.File;
import java.io.IOException;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.logging.Logger;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;


public class PopulationDensity extends JavaPlugin
{
	//for convenience, a reference to the instance of this plugin
	public static PopulationDensity instance;
	
	//for logging to the console and log file
	private static Logger log = Logger.getLogger("Minecraft");
		
	//developer configuration, not modifiable by users
	public static final int REGION_SIZE = 400;
	
	//the world managed by this plugin
	public static World ManagedWorld;
	
	//the nether world associated with the managed world
	public static World ManagedWorldNether;
	
	//the default world, not managed by this plugin
	//(may be null in some configurations)
	public static World CityWorld;
	
	//this handles data storage, like player and region data
	private DataStore dataStore;
	
	//user configuration, loaded/saved from a config.yml
	public boolean allowTeleportation;
	public boolean buildBreakAnywhere;
	public boolean teleportFromAnywhere;
	public boolean newPlayersSpawnInHomeRegion;
	public boolean respawnInHomeRegion;
	public boolean moveInRequiresInvitation;
	public String cityWorldName;
	public String managedWorldName;
	public int maxDistanceFromSpawnToUseHomeRegion;	
		
	public synchronized static void AddLogEntry(String entry)
	{
		log.info("PopDensity: " + entry);
	}
	
	//initializes well...   everything
	public void onEnable()
	{ 		
		AddLogEntry("PopulationDensity enabled.");		
		
		instance = this;
		
		//load the config if it exists
		FileConfiguration config = YamlConfiguration.loadConfiguration(new File(DataStore.configFilePath));
		
		//read configuration settings (note defaults)
		this.allowTeleportation = config.getBoolean("PopulationDensity.AllowTeleportation", true);
		this.buildBreakAnywhere = config.getBoolean("PopulationDensity.BuildBreakAnywhere", false);
		this.teleportFromAnywhere = config.getBoolean("PopulationDensity.TeleportFromAnywhere", false);
		this.newPlayersSpawnInHomeRegion = config.getBoolean("PopulationDensity.NewPlayersSpawnInHomeRegion", true);
		this.respawnInHomeRegion = config.getBoolean("PopulationDensity.RespawnInHomeRegion", true);
		this.moveInRequiresInvitation = config.getBoolean("PopulationDensity.MoveInRequiresInvitation", true);
		this.cityWorldName = config.getString("PopulationDensity.CityWorldName", "");
		this.maxDistanceFromSpawnToUseHomeRegion = config.getInt("PopulationDensity.MaxDistanceFromSpawnToUseHomeRegion", 25);
		this.managedWorldName = config.getString("PopulationDensity.ManagedWorldName", "Population Density Managed World");
		
		//write those values back and save. this ensures the config file is available on disk for editing
		config.set("PopulationDensity.NewPlayersSpawnInHomeRegion", this.newPlayersSpawnInHomeRegion);
		config.set("PopulationDensity.RespawnInHomeRegion", this.respawnInHomeRegion);
		config.set("PopulationDensity.CityWorldName", this.cityWorldName);
		config.set("PopulationDensity.AllowTeleportation", this.allowTeleportation);
		config.set("PopulationDensity.TeleportFromAnywhere", this.teleportFromAnywhere);
		config.set("PopulationDensity.BuildBreakAnywhere", this.buildBreakAnywhere);
		config.set("PopulationDensity.MoveInRequiresInvitation", this.moveInRequiresInvitation);
		config.set("PopulationDensity.MaxDistanceFromSpawnToUseHomeRegion", this.maxDistanceFromSpawnToUseHomeRegion);
		config.set("PopulationDensity.ManagedWorldName", this.managedWorldName);
		
		try
		{
			config.save(DataStore.configFilePath);
		}
		catch(IOException exception)
		{
			AddLogEntry("Unable to write to the configuration file at \"" + DataStore.configFilePath + "\"");
		}
		
		//get a reference to the managed world, creating it first if necessary
		ManagedWorld = this.getServer().getWorld(this.managedWorldName);
		if(ManagedWorld == null)
		{
			WorldCreator creator = WorldCreator.name(this.managedWorldName);
			creator.environment(Environment.NORMAL);
			ManagedWorld = creator.createWorld();
		}
		
		if(this.getServer().getAllowNether())
		{
			ManagedWorldNether = this.getServer().getWorld(this.managedWorldName + "_nether");
			if(ManagedWorldNether == null)
			{
				WorldCreator creator = WorldCreator.name(this.managedWorldName + "_nether");
				creator.environment(Environment.NETHER);
				ManagedWorldNether = creator.createWorld();
			}
		}
		
		//when datastore initializes, it loads player and region data, and posts some stats to the log
		this.dataStore = new DataStore();		
		
		//register for events
		PluginManager pluginManager = this.getServer().getPluginManager();
		
		//player events, to control spawn, respawn, disconnect, and region-based notifications as players walk around
		PlayerEventHandler playerEventHandler = new PlayerEventHandler(this.dataStore, this);
		pluginManager.registerEvent(Event.Type.PLAYER_JOIN, playerEventHandler, Event.Priority.Normal, this);
		pluginManager.registerEvent(Event.Type.PLAYER_QUIT, playerEventHandler, Event.Priority.Normal, this);
		pluginManager.registerEvent(Event.Type.PLAYER_MOVE, playerEventHandler, Event.Priority.Normal, this);		
		if(this.respawnInHomeRegion) pluginManager.registerEvent(Event.Type.PLAYER_RESPAWN, playerEventHandler, Event.Priority.Normal, this);
		if(this.getServer().getAllowNether()) pluginManager.registerEvent(Event.Type.PLAYER_PORTAL, playerEventHandler, Event.Priority.Normal, this);
		
		//block events, to limit building around region posts and in some other cases (config dependent)
		BlockEventHandler blockEventHandler = new BlockEventHandler(this.dataStore);
		pluginManager.registerEvent(Event.Type.BLOCK_BREAK, blockEventHandler, Event.Priority.Normal, this);
		pluginManager.registerEvent(Event.Type.BLOCK_PLACE, blockEventHandler, Event.Priority.Normal, this);
		
		//entity events, to protect region posts from explosions
		EntityEventHandler entityEventHandler = new EntityEventHandler();
		pluginManager.registerEvent(Event.Type.ENTITY_EXPLODE, entityEventHandler, Event.Priority.Normal, this);
		
		//scan the open region for resources and open a new one as necessary
		//may open and close several regions before finally leaving an "acceptable" region open
		this.updateOpenRegion();
		
		//make a note of the spawn world.  may be NULL if the configured city world name doesn't match an existing world
		CityWorld = this.getServer().getWorld(this.cityWorldName); 
	}
	
	public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args){
		
		Player player = null;
		if (sender instanceof Player) 
		{
			player = (Player) sender;
		}
		
		if(cmd.getName().equalsIgnoreCase("visitregion") && player != null)
		{
			
			if(args.length < 1) return false;
			
			//find the specified region, and send an error message if it's not found
			RegionCoordinates region = this.dataStore.getRegionCoordinates(args[0].toLowerCase());									
			if(region == null)
			{
				player.sendMessage("There's no region named \"" + args[0] + "\".  Unable to teleport.");
				return true;
			}
			
			if(!this.playerCanTeleport(player)) return true;
			
			//otherwise, teleport the user to the specified region					
			this.TeleportPlayer(player, region);
			
			return true;
		} 
		
		else if(cmd.getName().equalsIgnoreCase("newestregion") && player != null)
		{
			if(!this.playerCanTeleport(player)) return true;
			
			//teleport the user to the open region
			RegionCoordinates openRegion = this.dataStore.getOpenRegion();
			this.TeleportPlayer(player, openRegion);
			
			return true;
		}
		
		else if(cmd.getName().equalsIgnoreCase("homeregion") && player != null)
		{
			//when teleportation is disabled, homeregion still works, but only from the city world's spawn
			if(!this.allowTeleportation && CityWorld != null && player.getWorld().equals(CityWorld))
			{
				//max distance == 0 indicates no distance maximum
				if(		player.hasPermission("populationdensity.teleportanywhere") || 
						this.maxDistanceFromSpawnToUseHomeRegion < 1 || 
						player.getLocation().distance(CityWorld.getSpawnLocation()) < this.maxDistanceFromSpawnToUseHomeRegion)
				{
					TeleportPlayer(player, this.dataStore.getHomeRegionCoordinates(player));					
				}
				
				else
				{
					player.sendMessage("You're not close enough to the spawn to use this command.");
				}
				
				return true;
			}
			
			//check to ensure the player isn't already home
			RegionCoordinates homeRegion = this.dataStore.getHomeRegionCoordinates(player);
			if(!player.hasPermission("populationdensity.teleportanywhere") && !this.teleportFromAnywhere && homeRegion.equals(RegionCoordinates.fromLocation(player.getLocation())))
			{
				player.sendMessage("You're already in your home region.");
				return true;
			}
			
			//consider config, player location, player permissions
			if(this.playerCanTeleport(player))
				this.TeleportPlayer(player, homeRegion);
			
			return true;
		}
		
		else if((cmd.getName().equalsIgnoreCase("cityregion") || cmd.getName().equalsIgnoreCase("spawnregion")) && player != null)
		{
			//if city world isn't defined, this command isn't available
			if(CityWorld == null)
			{
				player.sendMessage("There's no city to send you to.");
				return true;
			}
			
			//when teleportation is disabled, this command still works when the player is close to his HOME region post
			if(!this.allowTeleportation)
			{			
				//close enough? teleport.
				AddLogEntry("" + getRegionCenter(this.dataStore.getHomeRegionCoordinates(player)).distanceSquared(player.getLocation()));
				AddLogEntry(getRegionCenter(this.dataStore.getHomeRegionCoordinates(player)).toVector().toString());
				AddLogEntry(player.getLocation().toVector().toString());
				
				Location centerOfHomeRegion = getRegionCenter(this.dataStore.getHomeRegionCoordinates(player));
				GuaranteeChunkLoaded(centerOfHomeRegion.getBlockX(), centerOfHomeRegion.getBlockZ());
				centerOfHomeRegion.setY(ManagedWorld.getHighestBlockYAt(centerOfHomeRegion));
				
				if(centerOfHomeRegion.distanceSquared(player.getLocation()) < 100 || player.hasPermission("populationdensity.teleportanywhere"))
				{
					Location teleportDestination = CityWorld.getHighestBlockAt(CityWorld.getSpawnLocation()).getLocation();
					teleportDestination.setY(teleportDestination.getY() + 3);
					player.teleport(teleportDestination);
				}
				
				//otherwise, error message
				else
				{
					player.sendMessage("You must be close to your home region's post to use this command.");
					player.sendMessage("On the surface, look for a glowing pillar on a wooden platform.");
				}
				
				return true;
			}
			
			//otherwise teleportation is enabled, so consider config, player location, player permissions					
			if(this.playerCanTeleport(player))
				player.teleport(CityWorld.getSpawnLocation());
			
			return true;
		}
		
		else if(cmd.getName().equalsIgnoreCase("randomregion") && player != null)
		{
			if(!this.playerCanTeleport(player)) return true;
			
			RegionCoordinates randomRegion = this.dataStore.getRandomRegion();
			
			this.TeleportPlayer(player, randomRegion);
			
			return true;
		}
		
		else if(cmd.getName().equalsIgnoreCase("invitetoregion") && player != null)
		{
			if(args.length < 1) return false;
			
			//figure out the player's home region
			RegionCoordinates homeRegion = this.dataStore.getHomeRegionCoordinates(player);
			
			//record the invitation
			this.dataStore.setInvitation(args[0], homeRegion);
			player.sendMessage("Invitation sent.  Your friend must use /acceptregioninvite to accept.");
			
			//send a notification to the invitee, if he's available
			Player invitee = this.getServer().getPlayer(args[0]);			
			if(invitee != null)
			{
				invitee.sendMessage(player.getName() + " has invited you to move into his or her home region!");
				invitee.sendMessage("You may only move once per week.  If you accept, you will be teleported to your new home immediately.");
				invitee.sendMessage("Use /acceptregioninvite to accept.");
			}
			
			return true;
		}
		
		else if(cmd.getName().equalsIgnoreCase("acceptregioninvite") && player != null)
		{
			//get the player's most recent invitation
			RegionCoordinates inviteRegion = this.dataStore.getInvitation(player);
			
			//if he doesn't have one, tell him so
			if(inviteRegion == null)
			{
				player.sendMessage("You haven't been invited to move into any regions.  A current resident must invite you with /invitetoregion first.");
				return true;
			}
			
			//if moved recently, send an error message
			
			//date of one week ago
			Calendar oneWeekAgo = Calendar.getInstance();
			oneWeekAgo.add(Calendar.DAY_OF_MONTH, -7);
			
			//date of last move
			Calendar lastMovedDate = Calendar.getInstance();
			lastMovedDate.setTime(this.dataStore.getLastMovedDate(player));
			
			//if date of last move is after the one week ago date, send error message
			if(lastMovedDate.compareTo(oneWeekAgo) >= 0)
			{
				player.sendMessage("Because you moved within the last week, you can't move again right now.");
				return true;
			}
			
			//if invite region is home region already, send error message
			if(this.dataStore.getHomeRegionCoordinates(player).equals(inviteRegion))
			{
				player.sendMessage("That region is already your home!");
				return true;
			}
			
			//otherwise, set new home and teleport the player there right away
			this.dataStore.setHomeRegionCoordinates(player, inviteRegion);
			this.TeleportPlayer(player, inviteRegion);
			
			return true;
		}
		
		else if(cmd.getName().equalsIgnoreCase("movein") && player != null)
		{
			//if not in the managed world, /movein doesn't make sense
			if(!player.getWorld().equals(ManagedWorld))
			{
				player.sendMessage("Sorry, no one can move in here.");
				return true;
			}
			
			//if already at home, send an error message
			RegionCoordinates homeRegion = this.dataStore.getHomeRegionCoordinates(player);
			RegionCoordinates currentRegion = RegionCoordinates.fromLocation(player.getLocation());
			if(homeRegion.equals(currentRegion))
			{
				player.sendMessage("This region is already your home!");
				return true;
			}
			
			//if moved recently, send an error message
			
			//date of one week ago
			Calendar oneWeekAgo = Calendar.getInstance();
			oneWeekAgo.add(Calendar.DAY_OF_MONTH, -7);
			
			//date of last move
			Calendar lastMovedDate = Calendar.getInstance();
			lastMovedDate.setTime(this.dataStore.getLastMovedDate(player));
			
			//if date of last move is after the one week ago date, send error message
			if(lastMovedDate.compareTo(oneWeekAgo) >= 0)
			{
				player.sendMessage("Because you moved within the last week, you can't move again right now.");
				return true;
			}
			
			//except for the open region and wilderness regions, moving-in requires an invitation
			String regionName = this.dataStore.getRegionName(currentRegion);
			RegionCoordinates openRegion = this.dataStore.getOpenRegion();
			if(this.moveInRequiresInvitation && regionName != null && !currentRegion.equals(openRegion) && !currentRegion.equals(this.dataStore.getInvitation(player)))
			{
				player.sendMessage("To move in here, you must first be invited by a current resident.");
				return true;
			}			
			
			//otherwise if hasn't been warned yet, send a warning
			if(!this.dataStore.getWarnedAboutMoveIn(player))
			{			
				player.sendMessage("You may only move once per week.  If you're sure, use /movein again.");
				this.dataStore.setWarnedAboutMoveIn(player);
				return true;
			}
			
			//otherwise, let the move-in happen
			else
			{
				//if a wilderness region, add a region post
				if(regionName == null)
				{
					this.dataStore.AddRegionPost(currentRegion, true);
				}
				
				this.dataStore.setHomeRegionCoordinates(player, RegionCoordinates.fromLocation(player.getLocation()));
				this.dataStore.setLastMovedDate(player, new Date());
				player.sendMessage("Welcome to your new home!");
				if(!this.buildBreakAnywhere)
					player.sendMessage("You may now mine and build here.");
				instance.sendRegionExcept(player.getName() + " has moved into this region!", RegionCoordinates.fromLocation(player.getLocation()), player);
				return true;
			}
		}
		
		else if(cmd.getName().equalsIgnoreCase("addregion") && player != null)
		{			
			RegionCoordinates newRegion = this.dataStore.addRegion();
			this.getServer().broadcastMessage("Region \"" + capitalize(this.dataStore.getRegionName(newRegion)) + "\" is now open and accepting new residents!");
			if(this.allowTeleportation)
			{
				this.getServer().broadcastMessage("Use /NewestRegion to visit, and /HomeRegion to return home!");
			}
			this.nextResourceScanTime = null;
			this.updateOpenRegion();
			
			return true;
		}
		
		return false; 
	}
	
	public void onDisable()
	{ 
		AddLogEntry("PopulationDensity disabled.");
	}
	
	//examines configuration, player permissions, and player location to determine whether or not to allow a teleport
	private boolean playerCanTeleport(Player player)
	{
		//if the player has the permission for teleportation, always allow it
		if(player.hasPermission("populationdensity.teleportanywhere")) return true;
		
		//otherwise if teleportation is disabled, always deny
		if(!this.allowTeleportation)
		{
			player.sendMessage("Sorry, you don't have permission to use that command.");
			return false;
		}
		
		//if teleportation from anywhere is disabled
		RegionCoordinates homeRegion = this.dataStore.getHomeRegionCoordinates(player);
		if(!this.teleportFromAnywhere)
		{		
			//if the player is in the managed nether world, deny
			if(player.getWorld().equals(ManagedWorldNether))
			{
				player.sendMessage("You can't teleport from here!");
				return false;
			}
			
			//otherwise if in his home region but not close to the region post, deny
			else if(homeRegion.equals(RegionCoordinates.fromLocation(player.getLocation())))
			{
				//get center of region coordinates (location of region post)
				Location regionCenter = getRegionCenter(homeRegion);
				regionCenter = ManagedWorld.getHighestBlockAt(regionCenter).getLocation();
				
				//if the player is too far away, send an error message and don't teleport
				if(regionCenter.distanceSquared(player.getLocation()) > 100)
				{
					player.sendMessage("You're not close enough to the region post to teleport.");
					player.sendMessage("On the surface, look for a glowing yellow post on a wooden platform.");
					return false;
				}
			}
		}
		
		//in all other cases, allow the teleportation
		return true;
	}
	
	//teleports a player to a specific region of the managed world, notifying players of arrival/departure as necessary
	//players always land at the region's region post, which is placed on the surface at the center of the region
	public void TeleportPlayer(Player player, RegionCoordinates region)
	{
		//if in managed world before teleport
		if(player.getWorld().equals(ManagedWorld))
		{
			//inform players in that region of this player's departure
			RegionCoordinates previousRegion = RegionCoordinates.fromLocation((player.getLocation()));
			this.notifyRegionChange(player, previousRegion, null);			
		}
		
		//where specifically to send the player?
		Location teleportDestination = getRegionCenter(region);
		int x = teleportDestination.getBlockX();
		int z = teleportDestination.getBlockZ();
		
		//find a safe height, a couple of blocks above the surface
		GuaranteeChunkLoaded(x, z);
		Block highestBlock = ManagedWorld.getHighestBlockAt(x, z);
		teleportDestination = new Location(ManagedWorld, x, highestBlock.getY() + 2, z);
		
		//send him
		player.teleport(teleportDestination);
		
		//inform him and others of his arrival
		this.notifyRegionChange(player, null, region);
	}
	
	//when a player moves between regions, this notifies players in those regions as appropriate
	//one of the latter two params may be NULL when the player is arriving in or departing from the spawn world
	public void notifyRegionChange(Player player, RegionCoordinates previousRegion, RegionCoordinates currentRegion)
	{
		//if previous region is in the managed world, notify players in that region
		if(previousRegion != null)
		{
			instance.sendRegionExcept(player.getName() + " left.", previousRegion, player);
		}
		
		//if new region is in the managed world, notify players in that region (including the moving player)
		if(currentRegion != null)
		{
			//notify moving player
			String newRegionName = this.dataStore.getRegionName(currentRegion);
			if(newRegionName != null)
			{			
				player.sendMessage("Welcome to the \"" + capitalize(newRegionName) + "\" region.");
			}
		
			//special wilderness region case, because wilderness regions don't have names
			else
			{
				player.sendMessage("You're in the wilderness.");
				if(this.allowTeleportation)
					player.sendMessage("In case you get lost, remember /homeregion.");
			}
			
			//notify other players in the new region
			instance.sendRegionExcept(player.getName() + " has arrived.", currentRegion, player);
		}
	}
	
	//sends a message to all of the players in a region EXCEPT for the specified player
	//used to notify players when someone enters or leaves the region they're in
	public void sendRegionExcept(String message, RegionCoordinates targetRegion, Player playerToExclude)
	{
		if(targetRegion == null) return;
		
		List<Player> managedWorldPlayers = ManagedWorld.getPlayers();
		for(int i = 0; i < managedWorldPlayers.size(); i++)
		{
			Player player = managedWorldPlayers.get(i);
			if(player.equals(playerToExclude)) continue;
			
			if(targetRegion.equals(RegionCoordinates.fromLocation(player.getLocation())))
			{
				player.sendMessage(message);
			}
		}
	}
	
	//earliest allowable time for the next resource scan
	private Calendar nextResourceScanTime = null;
	
	//scans the open region for resources and may close the region (and open a new one) if accessible resources are low
	//won't run unless it's been at least six hours since the last run
	//may repeat itself if the regions it opens are also not acceptably rich in resources
	public void updateOpenRegion()
	{						
		//what time is it?
		Calendar now = Calendar.getInstance();
		
		//if it's not time for another scan yet, stop here
		if(this.nextResourceScanTime != null && now.before(nextResourceScanTime)) return;
		
		//inform players about the reason for lag
		getServer().broadcastMessage("Examining regions for available resources... please wait!");		
		
		//update the earliest next scan time
		this.nextResourceScanTime = Calendar.getInstance();
		this.nextResourceScanTime.add(Calendar.HOUR, 6);
		
		RegionCoordinates region = this.dataStore.getOpenRegion();
		AddLogEntry(" Examining available resources in region at " + region.toString() + "...");
		
		boolean repeat;
		boolean alreadySlept = false;
		boolean regionAdded = false;
		do
		{
			region = this.dataStore.getOpenRegion();
			
			//initialize report content
			int woodCount = 0;
			int coalCount = 0;
			int ironCount = 0;
			int goldCount = 0;
			int redstoneCount = 0;
			int diamondCount = 0;
			int playerBlocks = 0;
	
			//initialize a new array to track where we've been
			int maxHeight = ManagedWorld.getMaxHeight();
			boolean [][][] examined = new boolean [REGION_SIZE][maxHeight + 1][REGION_SIZE];
			for(int x = 0; x < REGION_SIZE; x++)
				for(int y = 0; y < maxHeight + 1; y++)
					for(int z = 0; z < REGION_SIZE; z++)
						examined[x][y][z] = false;
			
			//determine start position, just above the surface int he center of the region
			Location startLocation = ManagedWorld.getHighestBlockAt(getRegionCenter(region)).getLocation();
			
			int start_x = startLocation.getBlockX();
			int start_y = startLocation.getBlockY() + 1;
			int start_z = startLocation.getBlockZ();			
			
			//set boundaries - horizontal boundaries reach to the region's edges
			int min_x = start_x - REGION_SIZE / 2 + 2;
			int max_x = start_x + REGION_SIZE / 2 - 2;			
			int min_z = start_z - REGION_SIZE / 2 + 2;
			int max_z = start_z + REGION_SIZE / 2 - 2;
			
			//set boundaries - vertical boundaries are based on distance from the start point
			//if a player has to climb a mountain or brave cavernous depths, those resources aren't "easily attainable"
			int min_y = start_y - 25;
			if(min_y < 2) min_y = 2;
			int max_y = start_y + 50;
			if(max_y > maxHeight - 2) max_y = maxHeight - 2;
			
			//instantiate empty queue
			ConcurrentLinkedQueue<Block> unexaminedBlockQueue = new ConcurrentLinkedQueue<Block>();
			
			//mark start block as examined
			int region_relative_x = start_x - (region.x * REGION_SIZE);
			int region_relative_z = start_z - (region.z * REGION_SIZE);
			try
			{
				examined[region_relative_x][start_y][region_relative_z] = true;
			}
			catch(ArrayIndexOutOfBoundsException e)
			{
				AddLogEntry("Unexpected Exception: " + e.toString());
			}
			
			//enqueue start block
			Chunk chunk = ManagedWorld.getChunkAt(startLocation);
			while(!chunk.isLoaded() || !chunk.load(true)); 
			Block currentBlock = ManagedWorld.getBlockAt(start_x, start_y, start_z);
			unexaminedBlockQueue.add(currentBlock);		
					
			//as long as there are blocks in the queue, keep going
			while(!unexaminedBlockQueue.isEmpty())
			{
				//dequeue a block
				currentBlock = unexaminedBlockQueue.remove();		
				
				//if not a pass-through block, consider material
				Material material = currentBlock.getType();
				if(		material != Material.AIR && 
						material != Material.WOOD_DOOR && 
						material != Material.WOODEN_DOOR &&
						material != Material.IRON_DOOR_BLOCK && 
						material != Material.TRAP_DOOR &&
						material != Material.LADDER
						)
				{
					if(material == Material.LOG) woodCount++;
					else if (material == Material.COAL_ORE) coalCount++;
					else if (material == Material.IRON_ORE) ironCount++;
					else if (material == Material.GOLD_ORE) goldCount++;
					else if (material == Material.REDSTONE_ORE) redstoneCount++;
					else if (material == Material.DIAMOND_ORE) diamondCount++;	
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
						//AddLogEntry("PLAYER BLOCK " + material.name());
						playerBlocks++;
					}
				}
				
				//otherwise for pass-through blocks, continue searching
				else
				{
					//get its location
					int current_x = currentBlock.getX();
					int current_y = currentBlock.getY();
					int current_z = currentBlock.getZ();
									
					//if this block is in bounds
					if(	current_x >= min_x && current_x <= max_x &&
						current_y >= min_y && current_y <= max_y && 
						current_z >= min_z && current_z <= max_z )
					{
					
						//make a list of adjacent blocks
						ConcurrentLinkedQueue<Block> adjacentBlockQueue = new ConcurrentLinkedQueue<Block>();
											
						//x + 1
						GuaranteeChunkLoaded(current_x + 1, current_z);
						adjacentBlockQueue.add(ManagedWorld.getBlockAt(current_x + 1, current_y, current_z));
						
						//x - 1
						GuaranteeChunkLoaded(current_x - 1, current_z);
						adjacentBlockQueue.add(ManagedWorld.getBlockAt(current_x - 1, current_y, current_z));
						
						//z + 1
						GuaranteeChunkLoaded(current_x, current_z + 1);
						adjacentBlockQueue.add(ManagedWorld.getBlockAt(current_x, current_y, current_z + 1));
						
						//z - 1
						GuaranteeChunkLoaded(current_x, current_z - 1);
						adjacentBlockQueue.add(ManagedWorld.getBlockAt(current_x, current_y, current_z - 1));
						
						//y + 1
						GuaranteeChunkLoaded(current_x, current_z);
						adjacentBlockQueue.add(ManagedWorld.getBlockAt(current_x, current_y + 1, current_z));
						
						//y - 1
						GuaranteeChunkLoaded(current_x, current_z);
						adjacentBlockQueue.add(ManagedWorld.getBlockAt(current_x, current_y - 1, current_z));
											
						//for each adjacent block
						while(!adjacentBlockQueue.isEmpty())
						{
							Block adjacentBlock = adjacentBlockQueue.remove();
							
							region_relative_x = adjacentBlock.getX() - (region.x * REGION_SIZE);
							region_relative_z = adjacentBlock.getZ() - (region.z * REGION_SIZE);						
							
							try
							{
								//if it hasn't been examined yet
								if(!examined[region_relative_x][adjacentBlock.getY()][region_relative_z])
								{					
									//mark it as examined
									examined[region_relative_x][adjacentBlock.getY()][region_relative_z] = true;
									
									//shove it in the queue for processing
									unexaminedBlockQueue.add(adjacentBlock);
								}
							}
							catch(ArrayIndexOutOfBoundsException e)
							{
								AddLogEntry("Unexpected Exception: " + e.toString());
							}
						}					
					}
				}
			}			
			
			//compute a resource score
			int resourceScore = coalCount * 2 + ironCount * 3 + goldCount * 3 + redstoneCount * 3 + diamondCount * 4;
			
			//due to a race condition, bukkit might say a chunk is loaded when it really isn't.
			//in that case, bukkit will incorrectly report that all of the blocks in the chunk are air
			//strategy: if resource score and wood count are flat zero, the result is suspicious, so wait 5 seconds for chunks to load and start over
			//to avoid an infinite loop in a resource-bare region, maximum ONE repetition
			
			//if the report outcome is suspicious and we haven't already checked twice
			if(resourceScore == 0 && woodCount == 0 && !alreadySlept)
			{
				//plan to repeat the scan
				repeat = true;				
				try
				{
					//sleep 5 seconds
					Thread.sleep(5000);
				}
				catch(InterruptedException e) { }
				
				//remember that we've already tried sleeping once
				alreadySlept = true;
			}
			
			//otherwise deliver the report and take action based on the results
			else
			{
				//deliver report
				AddLogEntry("Resource report: ");
				AddLogEntry("");				
				AddLogEntry("         Wood :" + woodCount);
				AddLogEntry("         Coal :" + coalCount);
				AddLogEntry("         Iron :" + ironCount);
				AddLogEntry("         Gold :" + goldCount);
				AddLogEntry("     Redstone :" + redstoneCount);
				AddLogEntry("      Diamond :" + diamondCount);
				AddLogEntry("Player Blocks :" + playerBlocks);
				AddLogEntry("");
				AddLogEntry(" Resource Score : " + resourceScore);
				
				//if NOT sufficient resources for a good start
				if(resourceScore < 200 || woodCount < 200 || playerBlocks > 1000)
				{					
					//add a new region and plan to repeat the process in that new region
					this.dataStore.addRegion();
					regionAdded = true;
					alreadySlept = false;
					repeat = true;
				}
				
				//otherwise we're done!
				else
				{
					repeat = false;
				}
			}
		}while(repeat);
		
		//if a region was opened, notify all players and make a log entry
		if(regionAdded)
		{
			String regionName = this.dataStore.getRegionName(this.dataStore.getOpenRegion());
			String regionAnnouncement = "Region \"" + capitalize(regionName) + "\" is now open for business!";
			AddLogEntry(regionAnnouncement);
			this.getServer().broadcastMessage(regionAnnouncement);
		}
	}
	
	//ensures a piece of the managed world is loaded into server memory
	//(generates the chunk if necessary)
	//these coordinate params are BLOCK coordinates, not CHUNK coordinates
	public static void GuaranteeChunkLoaded(int x, int z)
	{
		Location location = new Location(ManagedWorld, x, 5, z);
		Chunk chunk = ManagedWorld.getChunkAt(location);
		while(!chunk.isLoaded() || !chunk.load(true));
	}
	
	//determines the center of a region (as a Location) given its region coordinates
	//keeping all regions the same size and aligning them in a grid keeps this calculation simple and fast
	public static Location getRegionCenter(RegionCoordinates region)
	{
		int x, z;
		if(region.x >= 0)
			x = region.x * REGION_SIZE + REGION_SIZE / 2;
		else
			x = region.x * REGION_SIZE + REGION_SIZE / 2;
		
		if(region.z >= 0)
			z = region.z * REGION_SIZE + REGION_SIZE / 2;
		else
			z = region.z * REGION_SIZE + REGION_SIZE / 2;
		
		return new Location(ManagedWorld, x, 1, z);
	}
	
	//capitalizes a string, used to make region names pretty
	public static String capitalize(String string)
	{
		if(string == null || string.length() == 0) 
			
			return string;
		
		if(string.length() == 1)
			
			return string.toUpperCase();
		
		return string.substring(0, 1).toUpperCase() + string.substring(1);    
	}
}