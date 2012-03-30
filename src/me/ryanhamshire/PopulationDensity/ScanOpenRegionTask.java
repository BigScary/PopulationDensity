package me.ryanhamshire.PopulationDensity;

public class ScanOpenRegionTask implements Runnable 
{
	@Override
	public void run() 
	{
		PopulationDensity.instance.scanRegion(PopulationDensity.instance.dataStore.getOpenRegion(), true);		
	}	
}
