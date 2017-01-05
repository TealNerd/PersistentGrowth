package com.civclassic.persistentgrowth;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.CropState;
import org.bukkit.Material;
import org.bukkit.NetherWartsState;
import org.bukkit.TreeSpecies;
import org.bukkit.TreeType;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.StructureGrowEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.material.CocoaPlant;
import org.bukkit.material.CocoaPlant.CocoaPlantSize;
import org.bukkit.material.Crops;
import org.bukkit.material.MaterialData;
import org.bukkit.material.NetherWarts;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

public class GrowthListener implements Listener {

	private GrowthStorage storage;
	private String messageFormat = "[PersistentGrowth] %s %s in %02d hours, %02d minutes, %02d seconds";
	BlockFace[] surrounding = new BlockFace[]{BlockFace.EAST, BlockFace.WEST, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.UP};
	
	public GrowthListener(GrowthStorage storage) {
		this.storage = storage;
	}
	
	@EventHandler
	public void onBlockGrow(BlockGrowEvent event) {
		MaterialData mat = event.getNewState().getData();
		if(Config.getForPlant(mat) != null) {
			event.setCancelled(true);
			growChunk(event.getBlock().getChunk(), event.getBlock(), null);
		}
	}
	
	@EventHandler
	public void onStructureGrow(StructureGrowEvent event) {
		if(event.isFromBonemeal()) {
			event.setCancelled(true);
			return;
		}
		TreeType type = event.getSpecies();
		if(type == TreeType.BIG_TREE) {
			event.setCancelled(true);
			return;
		}
		Block block = event.getLocation().getBlock();
		if(Config.getForPlant(block.getState().getData()) != null) {
			event.setCancelled(true);
			growChunk(block.getChunk(), block, null);
		}
	}
	
	@EventHandler
	public void onPlayerInteract(PlayerInteractEvent event) {
		if(event.getAction() == Action.RIGHT_CLICK_BLOCK) {
			if(event.hasItem() && event.getItem().getType() == Material.INK_SACK && event.getItem().getDurability() == 15) {
				growChunk(event.getClickedBlock().getChunk(), event.getClickedBlock(), event.getPlayer());
			}
		} else if(event.getAction() == Action.LEFT_CLICK_BLOCK) {
			if(event.hasItem()) {
				Material type = MaterialAliases.getBlockFromItem(event.getItem().getType());
				if(type != null) {
					GrowthConfig config = Config.getForPlant(new MaterialData(type));
					if(config != null) {
						String biome = event.getClickedBlock().getBiome().toString();
						double growthMod = config.getGrowthModifier(biome);
						long time = (long) (Config.getBaseGrowTime() * growthMod);
						if(Config.requireSunlight() && !hasFullLight(event.getClickedBlock())) {
							time = Long.MAX_VALUE;
						}
						sendPlayerMessage(event.getPlayer(), time, false, type.toString());
					}
				}
			}
		}
	}
	
	List<TreeSpecies> bigTrees = Arrays.asList(new TreeSpecies[] {TreeSpecies.DARK_OAK, TreeSpecies.JUNGLE, TreeSpecies.REDWOOD});
	@EventHandler
	public void onBlockPlace(BlockPlaceEvent event) {
		if(Config.getForPlant(event.getBlock().getState().getData()) != null) {
			Block plant = event.getBlock();
			if(plant.getType() == Material.CACTUS || plant.getType() == Material.SUGAR_CANE_BLOCK) {
				Block at = plant.getRelative(BlockFace.DOWN);
				if(at.getType() == plant.getType()) return; //not the bottom block of the cactus, dont worry about it
			}
			storage.addPlant(plant);
		}
	}
	
	@EventHandler
	public void onBlockBreak(BlockBreakEvent event) {
		MaterialData data = event.getBlock().getState().getData();
		if(Config.getForPlant(data) != null) {
			storage.removePlant(event.getBlock());
			boolean doDrops = false;
			if(data instanceof Crops) {
				doDrops = ((Crops)data).getState() == CropState.RIPE;
			} else if(data instanceof NetherWarts) {
				doDrops = ((NetherWarts)data).getState() == NetherWartsState.RIPE;
			} else if(data instanceof CocoaPlant) {
				doDrops = ((CocoaPlant)data).getSize() == CocoaPlantSize.LARGE;
			}
			if(doDrops) {
				GrowthConfig config = Config.getForPlant(data);
				DropConfig drop = config.getDrop(event.getBlock().getBiome().toString());
				if(drop != null) {
					for(ItemStack item : drop.getDrops()) {
						new BukkitRunnable() {
							public void run() {
								event.getBlock().getWorld().dropItem(event.getBlock().getLocation().add(0.5, 0.5, 0.5), item).setVelocity(new Vector(0, 0.05, 0));
							}
						}.runTaskLater(PersistentGrowth.instance(), 1);
					}
				}
			}
		}
	}
	
	private void growChunk(Chunk chunk, Block clicked, Player player) {
		Map<ChunkPos, Long> plants = storage.getPlantsForChunk(chunk);
		for(ChunkPos pos : plants.keySet()) {
			Block next = chunk.getBlock(pos.getX(), pos.getY(), pos.getZ());
			if(next.getLocation().equals(clicked.getLocation())) {
				growPlant(next, player);
			} else {
				growPlant(next, null);
			}
		}
	}
	
	private void growPlant(Block block, Player player) {
		GrowthConfig config = Config.getForPlant(block.getState().getData());
		if(PlantUtils.isFruitFul(block.getType()) && !PlantUtils.hasFruit(block)
				&& ((Crops)block.getState().getData()).getState() == CropState.RIPE) {
			config = Config.getForPlant(new MaterialData(PlantUtils.getFruit(block.getType())));
		}
		if(config == null) {
			storage.removePlant(block);
			return;
		}
		String biome = block.getBiome().toString();
		double growthMod = config.getGrowthModifier(biome);
		long time = (long) (Config.getBaseGrowTime() * growthMod);
		long remaining = time - (System.currentTimeMillis() - storage.getPlantedTime(block));
		if(Config.requireSunlight() && !hasFullLight(block)) {
			remaining = Long.MAX_VALUE;
		}
		if(remaining <= 0) {
			if(PlantUtils.isFruitFul(block.getType()) && !PlantUtils.hasFruit(block)
					&&((Crops)block.getState().getData()).getState() == CropState.RIPE) {
				PlantUtils.getFreeBlock(block).setType(PlantUtils.getFruit(block.getType()));
				storage.removePlant(block);
				return;
			}
			BlockState state = block.getState();
			MaterialData data = state.getData();
			if(data instanceof Crops) {
				Crops crop = (Crops) data;
				crop.setState(CropState.RIPE);
				state.setData(crop);
			} else if(data instanceof CocoaPlant) {
				CocoaPlant cocoa = (CocoaPlant) data;
				cocoa.setSize(CocoaPlantSize.LARGE);
				state.setData(cocoa);
			} else if(data instanceof NetherWarts) {
				NetherWarts warts = (NetherWarts) data;
				warts.setState(NetherWartsState.RIPE);
				state.setData(warts);
			} else if(data.getItemType() == Material.SUGAR_CANE_BLOCK || data.getItemType() == Material.CACTUS) {
				for(int i = 1; i < 3; i++) {
					Block next = block.getRelative(BlockFace.UP, i);
					if(next.getType() == Material.AIR) {
						next.setType(data.getItemType());
						if(data.getItemType() == Material.CACTUS) {
							for(BlockFace face : surrounding) {
								if(next.getRelative(face).getType() != Material.AIR) {
									next.breakNaturally();
									break;
								}
							}
						}
						break;
					}
				}
			} else if(data.getItemType() == Material.SAPLING) {
				TreeType type = PlantUtils.getTreeType(block);
				if(type != null) {
					if(type == TreeType.JUNGLE || type == TreeType.MEGA_REDWOOD || type == TreeType.DARK_OAK) {
						for(BlockFace face : PlantUtils.largeTreeBlocks) {
							Block at = block.getRelative(face);
							at.setType(Material.AIR);
							storage.removePlant(at);
						}
					}
					block.setType(Material.AIR);
					block.getWorld().generateTree(block.getLocation(), type);
				}
			}
			state.update(true, false);
			if(PlantUtils.isFruitFul(state.getType()) || state.getType() == Material.CACTUS || state.getType() == Material.SUGAR_CANE_BLOCK) {
				storage.resetTime(block);
			} else {
				storage.removePlant(block);
			}
		}
		if(player != null) {
			long duration = remaining <= 0 ? time : remaining;
			sendPlayerMessage(player, duration, remaining <= 0, config.getPlantType().toString());
		}
	}
	
	private void sendPlayerMessage(Player player, long duration, boolean grown, String plantType) {
		String message = "";
		if(grown) {
			long hours = TimeUnit.MILLISECONDS.toHours(duration);
			duration -= TimeUnit.HOURS.toMillis(hours);
			long minutes = TimeUnit.MILLISECONDS.toMinutes(duration);
			duration -= TimeUnit.MINUTES.toMillis(minutes);
			long seconds = TimeUnit.MILLISECONDS.toSeconds(duration);
			message = String.format(messageFormat,
					plantType, "grew",
					hours, minutes, seconds);
		} else {
			long hours = TimeUnit.MILLISECONDS.toHours(duration);
			duration -= TimeUnit.HOURS.toMillis(hours);
			long minutes = TimeUnit.MILLISECONDS.toMinutes(duration);
			duration -= TimeUnit.MINUTES.toMillis(minutes);
			long seconds = TimeUnit.MILLISECONDS.toSeconds(duration);
			message = String.format(messageFormat, 
					plantType, "will grow in",
					hours, minutes, seconds);
		}
		player.sendMessage(ChatColor.DARK_GRAY + message);
	}
	
	public boolean hasFullLight(Block block) {
		boolean light = true;
		for(int y = 1; y < block.getWorld().getMaxHeight() - block.getY(); y++) {
			Block next = block.getRelative(BlockFace.UP, y);
			if(next.getType() != Material.AIR && next.getType() != Material.LEAVES && next.getType() != Material.LEAVES_2) {
				light = false;
			}
		}
		if(!light) {
			for(BlockFace face : surrounding) {
				if(block.getRelative(face).getType() == Material.GLOWSTONE) {
					light = true;
					break;
				}
			}
		}
		return light;
	}
	
	public Material getGrownAlias(Material plant) {
		switch (plant) {
		case MELON_STEM:
			return Material.MELON_BLOCK;
		case PUMPKIN_STEM:
			return Material.PUMPKIN;
		default: return plant;
		}
	}
}
