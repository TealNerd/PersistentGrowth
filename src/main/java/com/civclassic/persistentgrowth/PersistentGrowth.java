package com.civclassic.persistentgrowth;

import java.sql.SQLException;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.scheduler.BukkitRunnable;

import vg.civcraft.mc.civmodcore.ACivMod;
import vg.civcraft.mc.civmodcore.dao.ManagedDatasource;

public class PersistentGrowth extends ACivMod {

	private static PersistentGrowth instance;
	
	private GrowthStorage storage;
	private int updateTask = -1;
	
	public void onEnable() {
		instance = this;
		super.onEnable();
		setupDatabase();
		if(!getServer().getPluginManager().isPluginEnabled(this)) return;
		Config.load(getConfig());
		getServer().getPluginManager().registerEvents(new GrowthListener(storage), this);
		getCommand("persistentgrowth").setExecutor(this);
	}
	
	public void setupDatabase() {
		ConfigurationSerialization.registerClass(ManagedDatasource.class);
		ManagedDatasource db = null;
		try {
			db = (ManagedDatasource) getConfig().get("db");
			db.getConnection().close();
		} catch (SQLException e) {
			warning("Could not connnect to database, stopping PersistentGrowth",  e);
			getServer().getPluginManager().disablePlugin(this);
			return;
		}
		storage = new GrowthStorage(db);
		storage.registerMigrations();
		if(!db.updateDatabase()) {
			warning("Could not connnect to database, stopping PersistentGrowth");
			getServer().getPluginManager().disablePlugin(this);
			return;
		}
		long saveTicks = 1728000 / getConfig().getConfigurationSection("db").getLong("savesPerDay", 64);
		updateTask = new BukkitRunnable() {
			public void run() {
				storage.update();
			}
		}.runTaskTimerAsynchronously(this, saveTicks, saveTicks).getTaskId();
	}
	
	public void onDisable() {
		if(updateTask != -1) {
			Bukkit.getScheduler().cancelTask(updateTask);
			storage.update();
		}
	}
	
	@Override
	protected String getPluginName() {
		return "PersistentGrowth";
	}

	public static PersistentGrowth instance() {
		return instance;
	}
	
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		Config.clear();
		reloadConfig();
		Config.load(getConfig());
		sender.sendMessage("PersistentGrowth reloaded.");
		return true;
	}
}
