package me.ryanhamshire.PopulationDensity;

public class AddRegionPostTask implements Runnable 
{
	private RegionCoordinates region;
	private boolean updateNeighboringRegions;
	
	public AddRegionPostTask(RegionCoordinates region, boolean updateNeighboringRegions)
	{
		this.region = region;
		this.updateNeighboringRegions = updateNeighboringRegions;
	}
	
	@Override
	public void run() 
	{
		PopulationDensity.instance.dataStore.AddRegionPost(region, updateNeighboringRegions);		
	}
}
