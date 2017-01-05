package com.civclassic.persistentgrowth;

import java.util.List;
import java.util.Map;

import org.bukkit.inventory.ItemStack;

public class DropConfig {

	private List<ItemStack> drops;
	private double chance;
	private Map<String, Double> biomeModifiers;
	
	public DropConfig(List<ItemStack> drops, double chance, Map<String, Double> biomeModifiers) {
		this.drops = drops;
		this.chance = chance;
		this.biomeModifiers = biomeModifiers;
	}
	
	public double getChance(String biome) {
		double chance = this.chance;
		if(biomeModifiers.containsKey(biome)) {
			chance = biomeModifiers.get(biome);
		}
		return chance;
	}
	
	public List<ItemStack> getDrops() {
		return drops;
	}
}
