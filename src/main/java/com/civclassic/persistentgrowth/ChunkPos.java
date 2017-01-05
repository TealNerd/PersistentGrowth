package com.civclassic.persistentgrowth;

import org.bukkit.Location;

public class ChunkPos {

	private int x;
	private int y;
	private int z;
	
	public ChunkPos(int x, int y, int z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}
	
	public int getX() {
		return x;
	}
	
	public int getY() {
		return y;
	}
	
	public int getZ() {
		return z;
	}
	
	public ChunkPos(Location loc) {
		this(loc.getBlockX() - (loc.getChunk().getX() * 16), loc.getBlockY(), loc.getBlockZ() - (loc.getChunk().getZ() * 16));
	}
	
	public int hashCode() {
		return (x + "" + y + "" + z).hashCode();
	}
}
