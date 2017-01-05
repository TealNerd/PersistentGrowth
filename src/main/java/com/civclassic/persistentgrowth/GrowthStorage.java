package com.civclassic.persistentgrowth;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.block.Block;

import vg.civcraft.mc.civmodcore.dao.ManagedDatasource;

public class GrowthStorage {

	private ManagedDatasource db;
	private Map<PersistentChunk, Map<ChunkPos, Long>> chunks;
	private Map<PersistentChunk, Map<ChunkPos, Long>> pending;
	private Map<PersistentChunk, Integer> idMap;
	
	private final String createChunkTable = "create table if not exists %s ("
											+ "x int not null, "
											+ "y int not null,"
											+ "z int not null, "
											+ "time bigint default 0,"
											+ "unique key pos (x, y, z);";
	private final String insertChunkPos = "insert into %s (x, y, z, time) values (?,?,?,?);";
	private final String deleteFromChunk = "delete from %s where x=? and y=? and z=?;";
	private final String resetTime = "update %s set time=? where x=? and y=? and z=?;";
	
	public GrowthStorage(ManagedDatasource db) {
		this.db = db;
	}
	
	public void registerMigrations() {
		db.registerMigration(0, false, 
				"create table if not exists chunkidmap("
				+ "id int unsigned primary key auto_increment,"
				+ "x int not null,"
				+ "z int not null,"
				+ "world varchar(40) default '" + Bukkit.getWorlds().get(0).getUID().toString() + "');");
	}
	
	public void load() {
		chunks = new ConcurrentHashMap<PersistentChunk, Map<ChunkPos, Long>>();
		pending = new ConcurrentHashMap<PersistentChunk, Map<ChunkPos, Long>>();
		idMap = new ConcurrentHashMap<PersistentChunk, Integer>();
		try (Connection conn = db.getConnection();
				PreparedStatement ps = conn.prepareStatement("select * from chunkidmap;")) {
			ResultSet result = ps.executeQuery();
			while(result.next()) {
				UUID world = UUID.fromString(result.getString("world"));
				int cx = result.getInt("x");
				int cz = result.getInt("z");
				PersistentChunk chunk = new PersistentChunk(world, cx, cz);
				String chunkTable = "chunk_" + result.getInt("id");
				ResultSet plants = conn.prepareStatement("select * from " + chunkTable).executeQuery();
				Map<ChunkPos, Long> plantMap = new HashMap<ChunkPos, Long>();
				while(plants.next()) {
					int x = plants.getInt("x");
					int y = plants.getInt("y");
					int z = plants.getInt("z");
					long time = plants.getLong("time");
					plantMap.put(new ChunkPos(x,y,z), time);
				}
				chunks.put(chunk, plantMap);
			}
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	public void update() {
		Iterator<Entry<PersistentChunk, Map<ChunkPos, Long>>> iter = pending.entrySet().iterator();
		while(iter.hasNext()) {
			Entry<PersistentChunk, Map<ChunkPos, Long>> entry = iter.next();
			String chunkTable = "chunk_" + idMap.get(entry.getKey());
			Iterator<Entry<ChunkPos, Long>> posIter = entry.getValue().entrySet().iterator();
			while(posIter.hasNext()) {
				try (Connection conn = db.getConnection();
						PreparedStatement ps = conn.prepareStatement(String.format(insertChunkPos, chunkTable))) {
					int maxBatch = 100;
					int count = 0;
					while(posIter.hasNext() && count < maxBatch) {
						Entry<ChunkPos, Long> posEntry = posIter.next();
						ChunkPos pos = posEntry.getKey();
						ps.setInt(1, pos.getX());
						ps.setInt(2, pos.getY());
						ps.setInt(3, pos.getZ());
						ps.setLong(4, posEntry.getValue());
						ps.addBatch();
					}
					ps.executeBatch();
					count++;
					posIter.remove();
				} catch (SQLException e) {
					e.printStackTrace();
				}
			}
			iter.remove();
		}
	}
	
	public void addChunkTable(PersistentChunk chunk) {
		if(!chunks.containsKey(chunk)) {
			try (Connection conn = db.getConnection();
					PreparedStatement ps = conn.prepareStatement("insert into chunkidmap (x, z, world) values (?,?,?);")) {
				ps.setInt(1, chunk.getX());
				ps.setInt(2, chunk.getZ());
				ps.setString(3, chunk.getWorld().toString());
				ResultSet nid = ps.getGeneratedKeys();
				if(nid.next()) {
					idMap.put(chunk, nid.getInt(1));
					String tableName = "chunk_" + nid.getInt(1);
					conn.prepareCall(String.format(createChunkTable, tableName)).execute();
				}
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
	}
	
	public void addPlant(Block block) {
		PersistentChunk chunk = new PersistentChunk(block.getChunk());
		ChunkPos pos = new ChunkPos(block.getX(), block.getY(), block.getZ());
		long now = System.currentTimeMillis();
		if(!chunks.containsKey(chunk)) {
			chunks.put(chunk, new HashMap<ChunkPos, Long>());
			addChunkTable(chunk);
		}
		chunks.get(chunk).put(pos, now);
		if(!pending.containsKey(chunk)) {
			pending.put(chunk, new HashMap<ChunkPos, Long>());
		}
		pending.get(chunk).put(pos, now);
	}
	
	public long getPlantedTime(Block block) {
		PersistentChunk chunk = new PersistentChunk(block.getChunk());
		ChunkPos pos = new ChunkPos(block.getLocation());
		if(chunks.containsKey(chunk)) {
			if(chunks.get(chunk).containsKey(pos)) {
				return chunks.get(chunk).get(pos);
			}
		}
		return 0;
	}
	
	public Map<ChunkPos, Long> getPlantsForChunk(Chunk chunk) {
		PersistentChunk pChunk = new PersistentChunk(chunk);
		if(chunks.containsKey(pChunk)) {
			return chunks.get(pChunk);
		}
		return new HashMap<ChunkPos, Long>();
	}
	
	public void removePlant(Block block) {
		PersistentChunk chunk = new PersistentChunk(block.getChunk());
		ChunkPos pos = new ChunkPos(block.getLocation());
		String table = "chunk_" + idMap.get(chunk);
		try (Connection conn = db.getConnection();
				PreparedStatement ps = conn.prepareStatement(String.format(deleteFromChunk, table))) {
			ps.setInt(1, pos.getX());
			ps.setInt(2, pos.getY());
			ps.setInt(3, pos.getZ());
			ps.executeUpdate();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
	
	//used for cactus, sugarcan, melons, and pumpkins
	public void resetTime(Block block) {
		PersistentChunk chunk = new PersistentChunk(block.getChunk());
		ChunkPos pos = new ChunkPos(block.getLocation());
		String table = "chunk_" + idMap.get(chunk);
		try (Connection conn = db.getConnection();
				PreparedStatement ps = conn.prepareStatement(String.format(resetTime, table))) {
			ps.setLong(1, System.currentTimeMillis());
			ps.setInt(2, pos.getX());
			ps.setInt(3, pos.getY());
			ps.setInt(4, pos.getZ());
			ps.executeUpdate();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
}
