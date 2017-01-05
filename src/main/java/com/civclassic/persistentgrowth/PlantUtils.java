package com.civclassic.persistentgrowth;

import org.bukkit.Material;
import org.bukkit.TreeSpecies;
import org.bukkit.TreeType;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.material.Sapling;

public class PlantUtils {
	//What used to be "Trees"
	// positions of a 2x2 array, the center is the sapling northwest
	public static BlockFace[] largeTreeBlocks = new BlockFace[] {BlockFace.EAST,	BlockFace.SOUTH, BlockFace.SOUTH_EAST};
	
	// possible positions of 2x2 centers for a block
	static BlockFace[] largeTreeOriginBlocks = new BlockFace[] {BlockFace.WEST, BlockFace.NORTH, BlockFace.NORTH_WEST};

	public static TreeType getTreeType(Block block) {
		TreeType type = getTreeTypeFromSapling(block);
		
		if (type == TreeType.JUNGLE) {
			if (!canGrowLarge(block, type)) {
				// if a tree can't actually be a (large) JUNGLE tree, change to SMALL_JUNGLE unless part of a 2x2 array
				if (isPartOfLargeTree(block, type)) {
					// part of 2x2 array, abort and don't mess it up
					return null;
				} else {
					// was not part of any 2x2 array, and itself is not the northwest (center) of a 2x2, switch to SMALL_JUNGLE
					type = TreeType.SMALL_JUNGLE;
				}

			}
		} else if (type == TreeType.REDWOOD) {
			if (canGrowLarge(block, type)) {
				type = TreeType.MEGA_REDWOOD;
				
			} else if (isPartOfLargeTree(block, type)) {
				// part of 2x2 array, abort and don't mess it up
				return null;
			}

		} else if (type == TreeType.DARK_OAK) {
			if (!canGrowLarge(block, type)) {
				return null;
			}
			
		} else if (type == TreeType.TREE) {
			if (block.getBiome() == Biome.SWAMPLAND || block.getBiome() == Biome.MUTATED_SWAMPLAND) {
				// swamptree, only spawns naturally at worldgen
				type = TreeType.SWAMP;
			}

		}
		return type;
	}
	
	public static Block getLargeTreeOrigin(Block block, TreeType type) {
		type = getSaplingType(type);
		for (BlockFace face : largeTreeOriginBlocks) {
			Block candidate = block.getRelative(face);
			if (getTreeTypeFromSapling(candidate) == type && canGrowLarge(candidate, type)) {
				return candidate;
			}
		}
		return null;
	}

	private static boolean isPartOfLargeTree(Block block, TreeType type) {
		return getLargeTreeOrigin(block, type) != null;
	}

	public static boolean canGrowLarge(Block block, TreeType type) {
		type = getSaplingType(type);
		for (BlockFace face : largeTreeBlocks) {
			Block candidate = block.getRelative(face);
			if (getTreeTypeFromSapling(candidate) != type) {
				return false;
			}
		}
		return true;
	}

	private static TreeType getSaplingType(TreeType type) {
		if (type == TreeType.MEGA_REDWOOD) {
			return TreeType.REDWOOD;
		} else {
			return type;
		}
	}
	
	private static TreeType getTreeTypeFromSapling(Block block) {
		if(block.getState() instanceof Sapling) {
			return fromSpecies(((Sapling)block.getState()).getSpecies(), false);
		}
		return TreeType.TREE;
	}
	
	public static TreeType fromSpecies(TreeSpecies species, boolean mega) {
		switch(species) {
		case GENERIC:
			return TreeType.TREE;
		case REDWOOD:
			if(mega) {
				return TreeType.MEGA_REDWOOD;
			} else {
				return TreeType.REDWOOD;
			}
		case ACACIA:
			return TreeType.ACACIA;
		case BIRCH:
			return TreeType.BIRCH;
		case JUNGLE:
			if(mega) {
				return TreeType.SMALL_JUNGLE;
			} else {
				return TreeType.JUNGLE;
			}
		case DARK_OAK:
			return TreeType.DARK_OAK;
		}
		return TreeType.TREE;
	}
	
	//What used to be "Fruits"
	static BlockFace[] surroundingBlocks = new BlockFace[] {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
	
	public static boolean isFruitFul(Material material) {
		return material == Material.MELON_STEM || material == Material.PUMPKIN_STEM;
	}
	
	public static boolean isFruit(Material material) {
		return material == Material.MELON_BLOCK || material == Material.PUMPKIN;
	}

	public static boolean hasFruit(Block block) {
		return hasFruit(block, null);
	}

	/**
	 * Check if stem at block has ANY fruit next to it.
	 * @param block
	 * @param blockToIgnore Ignore this block for this check
	 * @return true if the stem has a fruit
	 */
	public static boolean hasFruit(Block block, Block blockToIgnore) {
		Material fruit = getFruit(block.getType());
		for(BlockFace face : surroundingBlocks) {
			Block candidate = block.getRelative(face);
			if (blockToIgnore != null && candidate.getLocation().equals(blockToIgnore.getLocation())) {
				continue;
			}
			if (candidate.getType() == fruit) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Find an air block over dirt, grass or farmland adjacent to given block.
	 * @param block Center to search from
	 * @param blockToIgnore Ignore these possibly adjacent blocks
	 * @return free block or null if none
	 */
	public static Block getFreeBlock(Block block) {
		Block air = null;
		for(BlockFace face : surroundingBlocks ) {
			Block candidate = block.getRelative(face);
			if (candidate.getType() == Material.AIR) {
				Material soil = candidate.getRelative(BlockFace.DOWN).getType();
				if (soil == Material.DIRT || soil == Material.SOIL || soil == Material.GRASS) {
					air = candidate;
				}
			}
		}
		return air;
	}

	public static Material getFruit(Material material) {
		if (material == Material.PUMPKIN_STEM) {
			return Material.PUMPKIN;
		} else if (material == Material.MELON_STEM) {
			return Material.MELON_BLOCK;
		} else {
			return null;
		}
	}
	
	public static Material getStem(Material material) {
		if (material == Material.PUMPKIN) {
			return Material.PUMPKIN_STEM;
		} else if (material == Material.MELON_BLOCK) {
			return Material.MELON_STEM;
		} else {
			return null;
		}
	}
}