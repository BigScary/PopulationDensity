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
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
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
	
	//the default world, not managed by this plugin
	//(may be null in some configurations)
	public static World CityWorld;
	
	//this handles data storage, like player and region data
	public DataStore dataStore;
	
	//user configuration, loaded/saved from a config.yml
	public boolean allowTeleportation;
	public boolean teleportFromAnywhere;
	public boolean newPlayersSpawnInHomeRegion;
	public boolean respawnInHomeRegion;
	public String cityWorldName;
	public String managedWorldName;
	public int maxDistanceFromSpawnToUseHomeRegion;
	public double densityRatio;
	public int maxIdleMinutes;
	public int minimumPlayersOnlineForIdleBoot;
	public boolean enableLoginQueue;	
	public int reservedSlotsForAdmins;
	public String queueMessage;
	public int hoursBetweenScans;
	public boolean buildRegionPosts;
	public boolean newestRegionRequiresPermission;
	public boolean regrowGrass;
	public boolean respawnAnimals;
	public boolean regrowTrees;
	
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
		
		//prepare default setting for managed world...
		String defaultManagedWorldName = "";
		
		//build a list of normal environment worlds 
		List<World> worlds = this.getServer().getWorlds();
		ArrayList<World> normalWorlds = new ArrayList<World>();
		for(int i = 0; i < worlds.size(); i++)
		{
			if(worlds.get(i).getEnvironment() == Environment.NORMAL)
			{
				normalWorlds.add(worlds.get(i));
			}
		}
		
		//if there's only one, make it the default
		if(normalWorlds.size() == 1)
		{
			defaultManagedWorldName = normalWorlds.get(0).getName();
		}
		
		//read configuration settings (note defaults)
		this.allowTeleportation = config.getBoolean("PopulationDensity.AllowTeleportation", true);
		this.teleportFromAnywhere = config.getBoolean("PopulationDensity.TeleportFromAnywhere", false);
		this.newPlayersSpawnInHomeRegion = config.getBoolean("PopulationDensity.NewPlayersSpawnInHomeRegion", true);
		this.respawnInHomeRegion = config.getBoolean("PopulationDensity.RespawnInHomeRegion", true);
		this.cityWorldName = config.getString("PopulationDensity.CityWorldName", "");
		this.maxDistanceFromSpawnToUseHomeRegion = config.getInt("PopulationDensity.MaxDistanceFromSpawnToUseHomeRegion", 25);
		this.managedWorldName = config.getString("PopulationDensity.ManagedWorldName", defaultManagedWorldName);
		this.densityRatio = config.getDouble("PopulationDensity.DensityRatio", 1.0);
		this.maxIdleMinutes = config.getInt("PopulationDensity.MaxIdleMinutes", 10);
		this.enableLoginQueue = config.getBoolean("PopulationDensity.LoginQueueEnabled", true);
		this.minimumPlayersOnlineForIdleBoot = config.getInt("PopulationDensity.MinimumPlayersOnlineForIdleBoot", this.getServer().getMaxPlayers() / 2);
		this.reservedSlotsForAdmins = config.getInt("PopulationDensity.ReservedSlotsForAdministrators", 1);
		if(this.reservedSlotsForAdmins < 0) this.reservedSlotsForAdmins = 0;
		this.queueMessage = config.getString("PopulationDensity.LoginQueueMessage", "%queuePosition% of %queueLength% in queue.  Reconnect within 3 minutes to keep your place.  :)");
		this.hoursBetweenScans = config.getInt("PopulationDensity.HoursBetweenScans", 6);
		this.buildRegionPosts = config.getBoolean("PopulationDensity.BuildRegionPosts", true);
		this.newestRegionRequiresPermission = config.getBoolean("PopulationDensity.NewestRegionRequiresPermission", false);
		this.regrowGrass = config.getBoolean("PopulationDensity.GrassRegrows", true);
		this.respawnAnimals = config.getBoolean("PopulationDensity.AnimalsRespawn", true);
		this.regrowTrees = config.getBoolean("PopulationDensity.TreesRegrow", true);
		
		//write those values back and save. this ensures the config file is available on disk for editing
		config.set("PopulationDensity.NewPlayersSpawnInHomeRegion", this.newPlayersSpawnInHomeRegion);
		config.set("PopulationDensity.RespawnInHomeRegion", this.respawnInHomeRegion);
		config.set("PopulationDensity.CityWorldName", this.cityWorldName);
		config.set("PopulationDensity.AllowTeleportation", this.allowTeleportation);
		config.set("PopulationDensity.TeleportFromAnywhere", this.teleportFromAnywhere);
		config.set("PopulationDensity.MaxDistanceFromSpawnToUseHomeRegion", this.maxDistanceFromSpawnToUseHomeRegion);
		config.set("PopulationDensity.ManagedWorldName", this.managedWorldName);
		config.set("PopulationDensity.DensityRatio", this.densityRatio);
		config.set("PopulationDensity.MaxIdleMinutes", this.maxIdleMinutes);
		config.set("PopulationDensity.LoginQueueEnabled", this.enableLoginQueue);
		config.set("PopulationDensity.MinimumPlayersOnlineForIdleBoot", this.minimumPlayersOnlineForIdleBoot);
		config.set("PopulationDensity.ReservedSlotsForAdministrators", this.reservedSlotsForAdmins);
		config.set("PopulationDensity.LoginQueueMessage", this.queueMessage);
		config.set("PopulationDensity.HoursBetweenScans", this.hoursBetweenScans);
		config.set("PopulationDensity.BuildRegionPosts", this.buildRegionPosts);
		config.set("PopulationDensity.NewestRegionRequiresPermission", this.newestRegionRequiresPermission);
		config.set("PopulationDensity.GrassRegrows", this.regrowGrass);
		config.set("PopulationDensity.AnimalsRespawn", this.respawnAnimals);
		config.set("PopulationDensity.TreesRegrow", this.regrowTrees);
		
		try
		{
			config.save(DataStore.configFilePath);
		}
		catch(IOException exception)
		{
			AddLogEntry("Unable to write to the configuration file at \"" + DataStore.configFilePath + "\"");
		}
		
		//get a reference to the managed world
		if(this.managedWorldName == null || this.managedWorldName.isEmpty())
		{
			PopulationDensity.AddLogEntry("Please specify a world to manage in config.yml.");
			return;
		}
		ManagedWorld = this.getServer().getWorld(this.managedWorldName);
		if(ManagedWorld == null)
		{
			PopulationDensity.AddLogEntry("Could not find a world named \"" + this.managedWorldName + "\".  Please update your config.yml.");
			return;
		}
		
		//when datastore initializes, it loads player and region data, and posts some stats to the log
		this.dataStore = new DataStore();
		
		//register for events
		PluginManager pluginManager = this.getServer().getPluginManager();
		
		//player events, to control spawn, respawn, disconnect, and region-based notifications as players walk around
		PlayerEventHandler playerEventHandler = new PlayerEventHandler(this.dataStore, this);
		pluginManager.registerEvents(playerEventHandler, this);
				
		//block events, to limit building around region posts and in some other cases (config dependent)
		BlockEventHandler blockEventHandler = new BlockEventHandler();
		pluginManager.registerEvents(blockEventHandler, this);
		
		//entity events, to protect region posts from explosions
		EntityEventHandler entityEventHandler = new EntityEventHandler();
		pluginManager.registerEvents(entityEventHandler, this);
		
		//world events, to generate region posts when chunks load
		WorldEventHandler worldEventHandler = new WorldEventHandler();
		pluginManager.registerEvents(worldEventHandler, this);
		
		//make a note of the spawn world.  may be NULL if the configured city world name doesn't match an existing world
		CityWorld = this.getServer().getWorld(this.cityWorldName);
		if(!this.cityWorldName.isEmpty() && CityWorld == null)
		{
			PopulationDensity.AddLogEntry("Could not find a world named \"" + this.cityWorldName + "\".  Please update your config.yml.");
		}
		
		//scan the open region for resources and open a new one as necessary
		//may open and close several regions before finally leaving an "acceptable" region open
		//this will repeat every six hours
		this.getServer().getScheduler().scheduleSyncRepeatingTask(this, new ScanOpenRegionTask(), 5L, this.hoursBetweenScans * 60 * 60 * 20L);
	}
	
	public boolean onCommand(CommandSender sender, Command cmd, String commandLabel, String[] args)
	{
		Player player = null;
		PlayerData playerData = null;
		if (sender instanceof Player)
		{
			player = (Player) sender;
			
			if(ManagedWorld == null)
			{
				player.sendMessage("The PopulationDensity plugin has not been properly configured.  Please update your config.yml to specify a world to manage.");
				return true;
			}
			
			playerData = this.dataStore.getPlayerData(player);
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
			
			if(!this.playerCanTeleport(player, false)) return true;
			
			//otherwise, teleport the user to the specified region					
			this.TeleportPlayer(player, region, false);
			
			return true;
		} 
		
		else if(cmd.getName().equalsIgnoreCase("newestregion") && player != null)
		{
			//check permission, if necessary
			if(this.newestRegionRequiresPermission && !player.hasPermission("populationdensity.newestregion"))
			{
				player.sendMessage("You don't have permission to use that command.");
				return true;
			}
			
			if(!this.playerCanTeleport(player, false)) return true;
			
			//teleport the user to the open region
			RegionCoordinates openRegion = this.dataStore.getOpenRegion();
			this.TeleportPlayer(player, openRegion, false);
			
			return true;
		}
		
		else if(cmd.getName().equalsIgnoreCase("whichregion") && player != null)
		{
			RegionCoordinates currentRegion = RegionCoordinates.fromLocation(player.getLocation());
			if(currentRegion == null)
			{
				player.sendMessage("You're not in a region!");
				return true;
			}
			
			String regionName = this.dataStore.getRegionName(currentRegion);
			if(regionName == null)
			{
				player.sendMessage("You're in the wilderness!  This region doesn't have a name.");
			}
			else
			{
				player.sendMessage("You're in the " + capitalize(regionName) + " region.");				
			}
			
			return true;
		}
		
		else if(cmd.getName().equalsIgnoreCase("nameregion") && player != null)
		{
			RegionCoordinates currentRegion = RegionCoordinates.fromLocation(player.getLocation());
			if(currentRegion == null)
			{
				player.sendMessage("You're not in a region!");
				return true;
			}
			
			String regionName = this.dataStore.getRegionName(currentRegion);
			if(regionName != null)
			{
				player.sendMessage("This region already has a name.");
			}
			else
			{
				//validate argument
				if(args.length < 1) return false;
				
				String name = args[0];
				
				if(name.length() > 10)
				{
					player.sendMessage("Region names must be at most 10 letters long.");
					return true;
				}
				
				for(int i = 0; i < name.length(); i++)
				{
					char c = name.charAt(i);
					if(Character.isWhitespace(c))
					{
						player.sendMessage("Region names must not include spaces.");
						return true;
					}
					
					if(!Character.isLetter(c))
					{
						player.sendMessage("Region names may only include letters.");
						return true;
					}					
				}
				
				//name region
				this.dataStore.nameRegion(currentRegion, name);
			}
			
			return true;
		}
		
		else if(cmd.getName().equalsIgnoreCase("addregionpost") && player != null)
		{
			RegionCoordinates currentRegion = RegionCoordinates.fromLocation(player.getLocation());
			if(currentRegion == null)
			{
				player.sendMessage("You're not in a region!");
				return true;
			}
			
			this.dataStore.AddRegionPost(currentRegion, false);
			
			return true;
		}
		
		else if(cmd.getName().equalsIgnoreCase("homeregion") && player != null)
		{
			//check to ensure the player isn't already home
			RegionCoordinates homeRegion = playerData.homeRegion;
			if(!player.hasPermission("populationdensity.teleportanywhere") && !this.teleportFromAnywhere && homeRegion.equals(RegionCoordinates.fromLocation(player.getLocation())))
			{
				player.sendMessage("You're already in your home region.");
				return true;
			}
			
			//consider config, player location, player permissions
			if(this.playerCanTeleport(player, true))
			{
				this.TeleportPlayer(player, homeRegion, false);
				return true;
			}
			
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
			
			//otherwise teleportation is enabled, so consider config, player location, player permissions					
			if(this.playerCanTeleport(player, true))
			{
				Location spawn = CityWorld.getSpawnLocation();
				
				Block block = spawn.getBlock();
				while(block.getType() != Material.AIR)
				{
					block = block.getRelative(BlockFace.UP);					
				}
				
				player.teleport(block.getLocation());
			}
			
			return true;
		}
		
		else if(cmd.getName().equalsIgnoreCase("randomregion") && player != null)
		{
			if(!this.playerCanTeleport(player, false)) return true;
			
			RegionCoordinates randomRegion = this.dataStore.getRandomRegion(RegionCoordinates.fromLocation(player.getLocation()));
			
			if(randomRegion == null)
			{
				player.sendMessage("Sorry, you're in the only region so far.  Over time, more regions will open.");
			}
			else
			{			
				this.TeleportPlayer(player, randomRegion, false);
			}
			
			return true;
		}
		
		else if(cmd.getName().equalsIgnoreCase("invitetoregion") && player != null)
		{
			if(args.length < 1) return false;
			
			//figure out the player's home region
			RegionCoordinates homeRegion = playerData.homeRegion;
			
			//send a notification to the invitee, if he's available
			Player invitee = this.getServer().getPlayer(args[0]);			
			if(invitee != null)
			{
				playerData = this.dataStore.getPlayerData(invitee);
				playerData.regionInvitation = homeRegion;
				player.sendMessage("Invitation sent.  " + invitee.getName() + " must use a region post to teleport to your region.");
				
				invitee.sendMessage(player.getName() + " has invited you to visit his or her home region!");
				invitee.sendMessage("Stand near a region post and use /AcceptRegionInvite to accept.");
			}
			else
			{
				player.sendMessage("There's no player named \"" + args[0] + "\" online right now.");
			}
			
			return true;
		}
		
		else if(cmd.getName().equalsIgnoreCase("acceptregioninvite") && player != null)
		{
			//if he doesn't have an invitation, tell him so
			if(playerData.regionInvitation == null)
			{
				player.sendMessage("You haven't been invited to visit any regions.  Another player must invite you with /InviteToRegion first.");
				return true;
			}
			else if(this.playerCanTeleport(player, false))
			{
				this.TeleportPlayer(player, playerData.regionInvitation, false);
			}
						
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
						
			RegionCoordinates currentRegion = RegionCoordinates.fromLocation(player.getLocation());
			String regionName = this.dataStore.getRegionName(currentRegion);
			
			if(currentRegion.equals(playerData.homeRegion))
			{
				player.sendMessage("This region is already your home!");
				return true;
			}
			
			//if a wilderness region, add a region post
			if(regionName == null)
			{
				this.dataStore.AddRegionPost(currentRegion, true);
			}
			
			playerData.homeRegion = RegionCoordinates.fromLocation(player.getLocation());
			this.dataStore.savePlayerData(player, playerData);
			player.sendMessage("Welcome to your new home!");
			player.sendMessage("Use /HomeRegion from any region post to return here.");
			player.sendMessage("Use /InviteToRegion to invite other players to teleport here.");

			return true;
		}
		
		else if(cmd.getName().equalsIgnoreCase("addregion") && player != null)
		{			
			player.sendMessage("Opened a new region and started a resource scan.  See console or server logs for details.");
			
			RegionCoordinates newRegion = this.dataStore.addRegion();			
			
			this.scanRegion(newRegion, true);
			
			return true;
		}
		
		else if(cmd.getName().equalsIgnoreCase("scanregion") && player != null)
		{			
			player.sendMessage("Started scan.  Check console or server logs for results.");
			this.scanRegion(RegionCoordinates.fromLocation(player.getLocation()), false);
			
			return true;
		}
		
		else if(cmd.getName().equalsIgnoreCase("loginpriority") && player != null)
		{					
			//requires exactly two parameters, the other player's name and the priority
			if(args.length != 2 && args.length != 1) return false;
			
			PlayerData targetPlayerData = null;
			OfflinePlayer targetPlayer = null;
			if (args.length > 0)
			{			
				//find the specified player
				targetPlayer = this.resolvePlayer(args[0]);
				if(targetPlayer == null)
				{
					player.sendMessage("Player \"" + args[0] + "\" not found.");
					return true;
				}
				
				targetPlayerData = this.dataStore.getPlayerData(targetPlayer);
				
				player.sendMessage(targetPlayer.getName() + "'s login priority: " + targetPlayerData.loginPriority + ".");
				
				if(args.length < 2) return false;  //usage displayed
			
				//parse the adjustment amount
				int priority;			
				try
				{
					priority = Integer.parseInt(args[1]);
				}
				catch(NumberFormatException numberFormatException)
				{
					return false;  //causes usage to be displayed
				}
				
				//set priority			
				if(priority > 100) priority = 100;
				else if(priority < 0) priority = 0;
				
				targetPlayerData.loginPriority = priority;
				this.dataStore.savePlayerData(targetPlayer, playerData);
				
				//confirmation message
				player.sendMessage("Set " + targetPlayer.getName() + "'s priority to " + priority + ".");
				
				return true;
			}
		}

		return false;
	}
	
	public void onDisable()
	{
		AddLogEntry("PopulationDensity disabled.");
	}
	
	//examines configuration, player permissions, and player location to determine whether or not to allow a teleport
	private boolean playerCanTeleport(Player player, boolean isHomeOrCityTeleport)
	{
		//if the player has the permission for teleportation, always allow it
		if(player.hasPermission("populationdensity.teleportanywhere")) return true;
		
		//if teleportation from anywhere is enabled, always allow it
		if(this.teleportFromAnywhere) return true;
		
		//avoid teleporting from other worlds
		if(!player.getWorld().equals(ManagedWorld))
		{
			player.sendMessage("You can't teleport from here!");
			return false;
		}
		
		//when teleportation isn't allowed, the only exceptions are city to home, and home to city
		if(!this.allowTeleportation)
		{
			if(!isHomeOrCityTeleport)
			{
				player.sendMessage("You're limited to /HomeRegion and /CityRegion here.");
				return false;
			}
			
			//if close to home post, go for it
			PlayerData playerData = this.dataStore.getPlayerData(player);
			Location homeCenter = getRegionCenter(playerData.homeRegion);
			if(homeCenter.distanceSquared(player.getLocation()) < 100) return true;
			
			//if city is defined and close to city post, go for it
			if(nearCityPost(player)) return true;
			
			player.sendMessage("You can't teleport from here!");
			return false;
		}
		
		//otherwise, any post is acceptable to teleport from or to
		else
		{
			RegionCoordinates currentRegion = RegionCoordinates.fromLocation(player.getLocation());
			Location currentCenter = getRegionCenter(currentRegion);
			if(currentCenter.distanceSquared(player.getLocation()) < 100) return true;
			
			if(nearCityPost(player)) return true;
			
			player.sendMessage("You're not close enough to a region post to teleport.");
			player.sendMessage("On the surface, look for a glowing yellow post on a stone platform.");
			return false;			
		}
	}
	
	private boolean nearCityPost(Player player)
	{
		if(CityWorld != null && player.getWorld().equals(CityWorld))
		{
			//max distance == 0 indicates no distance maximum
			return (this.maxDistanceFromSpawnToUseHomeRegion < 1 ||	player.getLocation().distance(CityWorld.getSpawnLocation()) < this.maxDistanceFromSpawnToUseHomeRegion);
		}
		
		return false;
	}

	//teleports a player to a specific region of the managed world, notifying players of arrival/departure as necessary
	//players always land at the region's region post, which is placed on the surface at the center of the region
	public void TeleportPlayer(Player player, RegionCoordinates region, boolean silent)
	{
		//where specifically to send the player?
		Location teleportDestination = getRegionCenter(region);
		int x = teleportDestination.getBlockX();
		int z = teleportDestination.getBlockZ();
		
		//find a safe height, a couple of blocks above the surface
		GuaranteeChunkLoaded(x, z);
		Block highestBlock = ManagedWorld.getHighestBlockAt(x, z);
		teleportDestination = new Location(ManagedWorld, x, highestBlock.getY() + 1, z);
		
		//send him
		player.teleport(teleportDestination);		
	}
	
	//scans the open region for resources and may close the region (and open a new one) if accessible resources are low
	//may repeat itself if the regions it opens are also not acceptably rich in resources
	public void scanRegion(RegionCoordinates region, boolean openNewRegions)
	{						
		AddLogEntry("Examining available resources in region \"" + region.toString() + "\"...");						
		
		Location regionCenter = getRegionCenter(region);
		int min_x = regionCenter.getBlockX() - REGION_SIZE / 2;
		int max_x = regionCenter.getBlockX() + REGION_SIZE / 2;			
		int min_z = regionCenter.getBlockZ() - REGION_SIZE / 2;
		int max_z = regionCenter.getBlockZ() + REGION_SIZE / 2;
		
		Chunk lesserBoundaryChunk = ManagedWorld.getChunkAt(new Location(ManagedWorld, min_x, 1, min_z));
		Chunk greaterBoundaryChunk = ManagedWorld.getChunkAt(new Location(ManagedWorld, max_x, 1, max_z));
				
		ChunkSnapshot [][] snapshots = new ChunkSnapshot[greaterBoundaryChunk.getX() - lesserBoundaryChunk.getX() + 1][greaterBoundaryChunk.getZ() - lesserBoundaryChunk.getZ() + 1];
		boolean snapshotIncomplete;
		do
		{
			snapshotIncomplete = false;
		
			for(int x = 0; x < snapshots.length; x++)
			{
				for(int z = 0; z < snapshots[0].length; z++)
				{
					//get the chunk, load it, generate it if necessary
					Chunk chunk = ManagedWorld.getChunkAt(x + lesserBoundaryChunk.getX(), z + lesserBoundaryChunk.getZ());
					while(!chunk.load(true));
					
					//take a snapshot
					ChunkSnapshot snapshot = chunk.getChunkSnapshot();
					
					//verify the snapshot by finding something that's not air
					boolean foundNonAir = false;
					for(int y = 0; y < ManagedWorld.getMaxHeight(); y++)
					{
						//if we find something, save the snapshot to the snapshot array
						if(snapshot.getBlockTypeId(0, y, 0) != Material.AIR.getId())
						{
							foundNonAir = true;
							snapshots[x][z] = snapshot;
							break;
						}
					}
					
					//otherwise, plan to repeat this process again after sleeping a bit
					if(!foundNonAir)
					{
						snapshotIncomplete = true;
					}					
				}
			}
			
			//if at least one snapshot was all air, sleep a second to let the chunk loader/generator
			//catch up, and then try again
			if(snapshotIncomplete)
			{
				try 
				{
					Thread.sleep(1000);
				} 			
				catch (InterruptedException e) { } 				
			}
			
		}while(snapshotIncomplete);
		
		//try to unload any chunks which don't have players nearby
		Chunk [] loadedChunks = PopulationDensity.ManagedWorld.getLoadedChunks();
		for(int i = 0; i < loadedChunks.length; i++)
		{
			loadedChunks[i].unload(true, true);  //save = true, safe = true
		}
		
		//collect garbage
		System.gc();
		
		//create a new task with this information, which will more completely scan the content of all the snapshots
		ScanRegionTask task = new ScanRegionTask(snapshots, openNewRegions);
		
		//run it in a separate thread		
		this.getServer().getScheduler().scheduleAsyncDelayedTask(this, task, 5L);		
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
		
		Location center = new Location(ManagedWorld, x, 1, z);
				
		//PopulationDensity.GuaranteeChunkLoaded(ManagedWorld.getChunkAt(center).getX(), ManagedWorld.getChunkAt(center).getZ());		
		center = ManagedWorld.getHighestBlockAt(center).getLocation();
		
		return center;
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

	public void resetIdleTimer(Player player)
	{
		//if idle kick is disabled, don't do anything here
		if(PopulationDensity.instance.maxIdleMinutes < 1) return;
		
		PlayerData playerData = this.dataStore.getPlayerData(player);
		
		//if there's a task already in the queue for this player, cancel it
		if(playerData.afkCheckTaskID >= 0)
		{
			PopulationDensity.instance.getServer().getScheduler().cancelTask(playerData.afkCheckTaskID);
		}
		
		//queue a new task for later
		//note: 20L ~ 1 second
		playerData.afkCheckTaskID = PopulationDensity.instance.getServer().getScheduler().scheduleSyncDelayedTask(PopulationDensity.instance, new AfkCheckTask(player, playerData), 20L * 60 * PopulationDensity.instance.maxIdleMinutes);
	}
	
	private OfflinePlayer resolvePlayer(String name) 
	{
		Player player = this.getServer().getPlayer(name);
		if(player != null) return player;
		
		OfflinePlayer [] offlinePlayers = this.getServer().getOfflinePlayers();
		for(int i = 0; i < offlinePlayers.length; i++)
		{
			if(offlinePlayers[i].getName().equalsIgnoreCase(name))
			{
				return offlinePlayers[i];
			}
		}
		
		return null;
	}
}