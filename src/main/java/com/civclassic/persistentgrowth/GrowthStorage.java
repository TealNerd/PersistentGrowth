package com.civclassic.persistentgrowth;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.block.Block;

import vg.civcraft.mc.civmodcore.dao.ManagedDatasource;

public class GrowthStorage {

	private ManagedDatasource db;
	private Map<UUID, Map<Long, Map<Integer, Long>>> chunkCache;
	private Map<UUID, Integer> worlds;
	private long chunkUnloadTime;
	private Map<Long, Integer> chunkUnloadTasks;
	
	public GrowthStorage(ManagedDatasource db) {
		this.db = db;
		chunkCache = new HashMap<UUID, Map<Long, Map<Integer, Long>>>();
		worlds = new HashMap<UUID, Integer>();
		chunkUnloadTime = PersistentGrowth.instance().getConfig().getLong("db.chunkUnloadTime", 12000);
		chunkUnloadTasks = new HashMap<Long, Integer>();
	}
	
	public void registerMigrations() {
		db.registerMigration(0, false, 
				"create table if not exists crops("
				+ "world int not null,"
				+ "chunk bigint not null,"
				+ "pos int not null,"
				+ "time bigint default 0,"
				+ "unique key loc(world, chunk, pos);");
		db.registerMigration(1, false, 
				"create table if not exists world_id("
				+ "uuid varchar(40) unique not null,"
				+ "id int primary key auto_increment);");
	}
	
	public void load() {
		try (Connection conn = db.getConnection();
				PreparedStatement ps = conn.prepareStatement("select * from world_id;")) {
			ResultSet result = ps.executeQuery();
			while(result.next()) {
				worlds.put(UUID.fromString(result.getString("uuid")), result.getInt("id"));
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	public void loadChunk(Chunk chunk) {
		long chunkId = (chunk.getX() << 32) + chunk.getZ();
		UUID worldId = chunk.getWorld().getUID();
		if(chunkUnloadTasks.containsKey(chunkId)) {
			int task = chunkUnloadTasks.remove(chunkId);
			Bukkit.getScheduler().cancelTask(task);
		}
		if(!chunkCache.get(worldId).containsKey(chunkId)) {
			chunkCache.get(worldId).put(chunkId, new HashMap<Integer, Long>());
			try (Connection conn = db.getConnection();
					PreparedStatement ps = conn.prepareStatement("select * from crops where world=? and chunk=?;")) {
				ps.setInt(1, worlds.get(chunk.getWorld().getUID()));
				ps.setLong(2, chunkId);
				ResultSet result = ps.executeQuery();
				while(result.next()) {
					chunkCache.get(worldId).get(chunkId).put(result.getInt("pos"), result.getLong("time"));
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}
	
	public void addPlant(Block block) {
		Chunk chunk = block.getChunk();
		long chunkId = (chunk.getX() << 32) + chunk.getZ();
		UUID worldId = block.getWorld().getUID();
		int x = block.getX() - (chunk.getX() * 16);
		int z = block.getZ() - (chunk.getZ() * 16);
		int pos = (block.getY() << 16) + (x << 8) + z;
		chunkCache.get(worldId).get(chunkId).put(pos, System.currentTimeMillis());
		if(chunkCache.get(worldId).get(chunkId).containsKey(pos)) {
			resetTime(block);
		} else {
			try (Connection conn = db.getConnection();
					PreparedStatement ps = conn.prepareStatement("insert into crops (world, chunk, pos, time) values (?,?,?,?);")) {
				ps.setInt(1, worlds.get(worldId));
				ps.setLong(2, chunkId);
				ps.setInt(3, pos);
				ps.setLong(4, System.currentTimeMillis());
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}
	
	public long getPlantedTime(Block block) {
		Chunk chunk = block.getChunk();
		long chunkId = (chunk.getX() << 32) + chunk.getZ();
		UUID worldId = block.getWorld().getUID();
		int x = block.getX() - (chunk.getX() * 16);
		int z = block.getZ() - (chunk.getZ() * 16);
		int pos = (block.getY() << 16) + (x << 8) + z;
		if(chunkCache.get(worldId).get(chunkId).containsKey(pos)) {
			return chunkCache.get(worldId).get(chunkId).get(pos);
		}
		return 0;
	}
	
	public Map<Integer, Long> getPlantsForChunk(Chunk chunk) {
		long chunkId = (chunk.getX() << 32) + chunk.getZ();
		if(chunkCache.get(chunk.getWorld().getUID()).containsKey(chunkId)) {
			return chunkCache.get(chunk.getWorld().getUID()).get(chunkId);
		}
		return new HashMap<Integer, Long>();
	}
	
	public void removePlant(Block block) {
		Chunk chunk = block.getChunk();
		long chunkId = (chunk.getX() << 32) + chunk.getZ();
		UUID worldId = block.getWorld().getUID();
		int x = block.getX() - (chunk.getX() * 16);
		int z = block.getZ() - (chunk.getZ() * 16);
		int pos = (block.getY() << 16) + (x << 8) + z;
		if(chunkCache.get(worldId).get(chunkId).remove(pos) != null) {
			Bukkit.getScheduler().runTaskAsynchronously(PersistentGrowth.instance(), new Runnable() {
				public void run() {
					try (Connection conn = db.getConnection();
							PreparedStatement ps = conn.prepareStatement("delete from crops where world=? and chunk=? and pos=?")) {
						ps.setInt(1, worlds.get(worldId));
						ps.setLong(2, chunkId);
						ps.setInt(3, pos);
						ps.executeUpdate();
					} catch (SQLException e) {
						e.printStackTrace();
					}
				}
			});
		}
	}
	
	//used for cactus, sugarcan, melons, and pumpkins
	private void resetTime(Block block) {
		Chunk chunk = block.getChunk();
		long chunkId = (chunk.getX() << 32) + chunk.getZ();
		UUID worldId = block.getWorld().getUID();
		int x = block.getX() - (chunk.getX() * 16);
		int z = block.getZ() - (chunk.getZ() * 16);
		int pos = (block.getY() << 16) + (x << 8) + z;
		try (Connection conn = db.getConnection();
				PreparedStatement ps = conn.prepareStatement("update crops set time=? where world=? and chunk=? and pos=?")) {
			ps.setLong(1, System.currentTimeMillis());
			ps.setInt(2, worlds.get(worldId));
			ps.setLong(3, chunkId);
			ps.setInt(4, pos);
			ps.executeUpdate();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	public void unloadChunk(Chunk chunk) {
		long chunkId = (chunk.getX() << 32) + chunk.getZ();
		int task = Bukkit.getScheduler().runTaskLater(PersistentGrowth.instance(), new Runnable() {
			public void run() {
				if(!chunk.isLoaded()) {
					chunkCache.get(chunk.getWorld().getUID()).remove(chunkId);
				}
			}
		}, chunkUnloadTime).getTaskId();
		chunkUnloadTasks.put(chunkId, task);
	}
}
