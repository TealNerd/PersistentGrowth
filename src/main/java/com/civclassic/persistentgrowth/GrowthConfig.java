package com.civclassic.persistentgrowth;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import org.bukkit.Material;
import org.bukkit.material.MaterialData;

public class GrowthConfig {

	private MaterialData plantType;
	private Map<String, Double> biomeModifiers;
	
	private double growthModifier;
	
	private List<String> drops;

	public GrowthConfig(MaterialData plantType, double growthModifier, Map<String, Double> biomeModifiers,
			List<String> drops) {
		this.plantType = plantType;
		this.biomeModifiers = biomeModifiers;
		this.growthModifier = growthModifier;
		this.drops = drops;
	}

	public MaterialData getPlant() {
		return plantType;
	}
	
	public Material getPlantType() {
		return plantType.getItemType();
	}
	
	public double getGrowthModifier(String biome) {
		double mod = growthModifier;
		if(biomeModifiers.containsKey(biome)) {
			mod *= biomeModifiers.get(biome);
		}
		return mod;
	}
	
	public DropConfig getDrop(String biome) {
		double dice = Math.random();
		double cumChance = 0.0d;
		double localChance = 0.0d;
		int counted = 0;
		Iterator<String> dropIter = drops.iterator();
		while(dropIter.hasNext()) {
			DropConfig drop = Config.getDropConfig(dropIter.next());
			if(drop == null) {
				dropIter.remove();
				continue;
			}
			localChance = drop.getChance(biome);
			if(dice >= cumChance && dice < cumChance + localChance) {
				return drop;
			}
			cumChance += localChance;
			counted++;
		}
		if(Config.isDebug()) {
			PersistentGrowth.instance().getLogger()
				.log(Level.INFO, "{0} tested {1} cumm {2} dice", new Object[] {counted, Double.toString(cumChance), Double.toString(dice)});
		}
		return null;
	}
	
	public List<String> getDrops() {
		return drops;
	}
	
	public double getBaseGrowthModifier() {
		return growthModifier;
	}
}
