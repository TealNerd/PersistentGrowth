package com.civclassic.persistentgrowth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.MaterialData;

public class Config {

	private static boolean isDebug;
	
	private static boolean requireSunlight;
	private static long baseGrowTime;
	private static Map<MaterialData, GrowthConfig> growthConfigs = new HashMap<MaterialData, GrowthConfig>();
	private static Map<String, DropConfig> dropConfigs = new HashMap<String, DropConfig>();
	
	public static void load(ConfigurationSection config) {
		isDebug = config.getBoolean("debug");
		requireSunlight = config.getBoolean("requireSunlight");
		baseGrowTime = config.getLong("baseGrowTime");
		PersistentGrowth.instance().info("Debug %s", isDebug ? "enabled" : "disabled");
		PersistentGrowth.instance().info("Base growth time: %s", PersistentGrowth.formatTime(baseGrowTime));
		PersistentGrowth.instance().info("Sunlight or glowstone is %srequired", requireSunlight ? "" : "not ");
		ConfigurationSection plants = config.getConfigurationSection("plants");
		for(String key : plants.getKeys(false)) {
			GrowthConfig plant = loadGrowthConfig(plants.getConfigurationSection(key));
			growthConfigs.put(plant.getPlant(), plant);
		}
		ConfigurationSection drops = config.getConfigurationSection("drops");
		for(String drop : drops.getKeys(false)) {
			dropConfigs.put(drop, loadDropConfig(drops.getConfigurationSection(drop)));
		}
	}
	
	public static void clear() {
		growthConfigs.clear();
		dropConfigs.clear();
	}
	
	private static GrowthConfig loadGrowthConfig(ConfigurationSection config) {
		Material type = Material.valueOf(config.getString("plant.type"));
		byte data = (byte) config.getInt("plant.data", 0);
		@SuppressWarnings("deprecation")
		MaterialData plant = new MaterialData(type, data);
		double growthModifier = config.getDouble("baseGrowthModifier", 1.0);
		Map<String, Double> biomeModifiers = new HashMap<String, Double>();
		List<String> drops = new ArrayList<String>();
		if(config.contains("biomes")) {
			ConfigurationSection biomes = config.getConfigurationSection("biomes");
			for(String biome : biomes.getKeys(false)) {
				biomeModifiers.put(biome, biomes.getDouble(biome));
			}
		}
		if(config.contains("specialDrops")) {
			drops = config.getStringList("specialDrops");
		}
		PersistentGrowth.instance().info("Loaded config for {0} with base growth modifier {1}", plant.toString(), growthModifier);
		if(biomeModifiers.size() > 0) {
			PersistentGrowth.instance().info("  Grows differently in these biomes:");
			for(String biome : biomeModifiers.keySet()) {
				PersistentGrowth.instance().info("    {0}: {1}%", biome, biomeModifiers.get(biome) * 100);
			}
		}
		if(drops.size() > 0) {
			PersistentGrowth.instance().info("  Drops:");
			for(String drop : drops) {
				PersistentGrowth.instance().info("    - {0}", drop);
			}
		}
		return new GrowthConfig(plant, growthModifier, biomeModifiers, drops);
	}
	
	@SuppressWarnings("unchecked")
	private static DropConfig loadDropConfig(ConfigurationSection config) {
		List<ItemStack> items = (List<ItemStack>) config.getList("items");
		double chance = config.getDouble("chance");
		Map<String, Double> biomeModifiers = new HashMap<String, Double>();
		if(config.contains("biomes")) {
			ConfigurationSection biomes = config.getConfigurationSection("biomes");
			for(String biome : biomes.getKeys(false)) {
				biomeModifiers.put(biome, biomes.getDouble(biome));
			}
		}
		PersistentGrowth.instance().info("Loaded drop config {0} with base chance of {1}", config.getName(), chance);
		for(ItemStack item : items) {
			PersistentGrowth.instance().info("  {0}", item.toString());
		}
		if(biomeModifiers.size() > 0) {
			PersistentGrowth.instance().info("  Chance for special biomes:");
			for(String biome : biomeModifiers.keySet()) {
				PersistentGrowth.instance().info("    {0}: {1}%", biome, biomeModifiers.get(biome) * 100);
			}
		}
		return new DropConfig(items, chance, biomeModifiers);
	}

	public static boolean requireSunlight() {
		return requireSunlight;
	}
	
	public static long getBaseGrowTime() {
		return baseGrowTime;
	}
	
	public static GrowthConfig getForPlant(MaterialData mat) {
		return growthConfigs.get(mat);
	}
	
	public static DropConfig getDropConfig(String key) {
		return dropConfigs.get(key);
	}
	
	public static boolean isDebug() {
		return isDebug;
	}
}
