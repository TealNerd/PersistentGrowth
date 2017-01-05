package com.civclassic.persistentgrowth;

import java.util.UUID;

import org.bukkit.Chunk;

public class PersistentChunk {

	private UUID world;
	private int x;
	private int z;
	
	public PersistentChunk(UUID world, int x, int z) {
		this.world = world;
		this.x = x;
		this.z = z;
	}
	
	public PersistentChunk(Chunk chunk) {
		this(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
	}

	public boolean equals(Object obj) {
		if(this == obj) return true;
		if(obj == null || !(obj instanceof PersistentChunk)) return false;
		PersistentChunk other = (PersistentChunk)obj;
		return other.world.equals(world) && other.x == x && other.z == z;
	}
	
	public String toString() {
		return world + "" + x + "" + z;
	}
	
	public int hashCode() {
		return toString().hashCode();
	}

	public int getX() {
		return x;
	}
	
	public int getZ() {
		return z;
	}
	
	public UUID getWorld() {
		return world;
	}
}
