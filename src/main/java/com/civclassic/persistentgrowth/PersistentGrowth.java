package com.civclassic.persistentgrowth;

import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import vg.civcraft.mc.civmodcore.ACivMod;
import vg.civcraft.mc.civmodcore.dao.ManagedDatasource;

public class PersistentGrowth extends ACivMod {

	private static PersistentGrowth instance;
	
	private GrowthStorage storage;
	
	public void onEnable() {
		instance = this;
		super.onEnable();
		saveDefaultConfig();
		reloadConfig();
		setupDatabase();
		if(!getServer().getPluginManager().isPluginEnabled(this)) return;
		Config.load(getConfig());
		getServer().getPluginManager().registerEvents(new GrowthListener(storage), this);
		getCommand("persistentgrowth").setExecutor(this);
	}
	
	public void setupDatabase() {
		ConfigurationSection config = getConfig().getConfigurationSection("db");
		String host = config.getString("host");
		int port = config.getInt("port");
		String user = config.getString("user");
		String pass = config.getString("password");
		String dbname = config.getString("database");
		int poolsize = config.getInt("poolsize");
		long connectionTimeout = config.getLong("connectionTimeout");
		long idleTimeout = config.getLong("idleTimeout");
		long maxLifetime = config.getLong("maxLifetime");
		ManagedDatasource db = null;
		try {
			db = new ManagedDatasource(this, user, pass, host, port, dbname, poolsize, connectionTimeout, idleTimeout, maxLifetime);
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
		storage.load();
	}
	
	public void onDisable() {
		//storage.close();
	}
	
	@Override
	protected String getPluginName() {
		return "PersistentGrowth";
	}

	public static PersistentGrowth instance() {
		return instance;
	}
	
	public static String formatTime(long millis) {
		long hours = TimeUnit.MILLISECONDS.toHours(millis);
		millis -= TimeUnit.HOURS.toMillis(hours);
		long minutes = TimeUnit.MILLISECONDS.toMinutes(millis);
		millis -= TimeUnit.MINUTES.toMillis(minutes);
		long seconds = TimeUnit.MILLISECONDS.toSeconds(millis);
		return String.format("%d hours, %d minutes, %d seconds", hours, minutes, seconds);
	}
	
	public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
		Config.clear();
		reloadConfig();
		Config.load(getConfig());
		sender.sendMessage("PersistentGrowth reloaded.");
		return true;
	}
}
