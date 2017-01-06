package com.civclassic.persistentgrowth;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

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
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;
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
	private String messageFormat = "[PersistentGrowth] %s %s in %s";
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
				if(Config.getForPlant(event.getClickedBlock().getState().getData()) != null) {
					PersistentGrowth.instance().debug("Stopped {0} from using bonemeal on a plant at {1}", event.getPlayer(), event.getClickedBlock().getLocation());
					event.setCancelled(true);
				}
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
						sendTimeMessage(event.getPlayer(), time, false, type.toString());
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
			PersistentGrowth.instance().debug("Adding plant placed at {0} to database", event.getBlock().getLocation());
			storage.addPlant(plant);
		}
	}
	
	@EventHandler
	public void onBlockBreak(BlockBreakEvent event) {
		MaterialData data = event.getBlock().getState().getData();
		if(Config.getForPlant(data) != null) {
			PersistentGrowth.instance().debug("Removing plant at {0} from db because it was broken by {1}", event.getBlock().getLocation(), event.getPlayer());
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
					if(Config.isDebug()) {
						YamlConfiguration serial = new YamlConfiguration();
						serial.set("Drops", drop.getDrops());
						PersistentGrowth.instance().debug("Found for {0}, triggered by block break by {1}.\n{2}",
								config.getPlant(), event.getPlayer(), serial.saveToString());
					}
					for(ItemStack item : drop.getDrops()) {
						new BukkitRunnable() {
							public void run() {
								event.getBlock().getWorld().dropItem(event.getBlock().getLocation().add(0.5, 0.5, 0.5), item).setVelocity(new Vector(0, 0.05, 0));
							}
						}.runTaskLater(PersistentGrowth.instance(), 1);
					}
				}
			}
			//Handle adding growth back to the stem of pumpking/melons
			if(PlantUtils.isFruit(data.getItemType())) {
				Block stem = PlantUtils.getStem(event.getBlock(), data.getItemType());
				if(stem != null) {
					storage.addPlant(stem);
				}
			}
		}
	}
	
	@EventHandler
	public void onChunkLoad(ChunkLoadEvent event) {
		PersistentGrowth.instance().debug("Chunk loaded {0}", event.getChunk());
		storage.loadChunk(event.getChunk());
	}
	
	@EventHandler
	public void onChunkUnload(ChunkUnloadEvent event) {
		PersistentGrowth.instance().debug("Chunk unloaded {0}", event.getChunk());
		storage.unloadChunk(event.getChunk());
	}
	
	private void growChunk(Chunk chunk, Block clicked, Player player) {
		PersistentGrowth.instance().debug("Growing chunk {0}, triggered by {1} at {2}", chunk, player, clicked.getLocation());
		Map<Integer, Long> plants = storage.getPlantsForChunk(chunk);
		for(Integer pos : plants.keySet()) {
			int y = pos >> 16;
			int x = (pos >> 8) & 0xFF;
			int z = pos & 0xFF;
			Block next = chunk.getBlock(x, y, z);
			if(next.getLocation().equals(clicked.getLocation())) {
				growPlant(next, player);
			} else {
				growPlant(next, null);
			}
		}
	}
	
	private void growPlant(Block block, Player player) {
		PersistentGrowth.instance().debug("Growing plant at {0}", block.getLocation());
		GrowthConfig config = Config.getForPlant(block.getState().getData());
		if(PlantUtils.isFruitFul(block.getType()) && !PlantUtils.hasFruit(block)
				&& ((Crops)block.getState().getData()).getState() == CropState.RIPE) {
			config = Config.getForPlant(new MaterialData(PlantUtils.getFruit(block.getType())));
		}
		if(config == null) {
			PersistentGrowth.instance().debug("Tried to grow plant without config, removing from database");
			storage.removePlant(block);
			return;
		}
		String biome = block.getBiome().toString();
		double growthMod = config.getGrowthModifier(biome);
		long time = (long) (Config.getBaseGrowTime() * growthMod);
		long remaining = time - (System.currentTimeMillis() - storage.getPlantedTime(block));
		if(Config.requireSunlight() && !hasFullLight(block)) {
			PersistentGrowth.instance().debug("Plant found with insufficient lighting");
			remaining = Long.MAX_VALUE;
		}
		if(remaining <= 0) {
			PersistentGrowth.instance().debug("Plant was fully grown, handling block update");
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
				storage.addPlant(block);
			} else {
				storage.removePlant(block);
			}
		}
		if(player != null) {
			long duration = remaining <= 0 ? time : remaining;
			sendTimeMessage(player, duration, remaining <= 0, config.getPlantType().toString());
		}
	}
	
	private void sendTimeMessage(Player player, long duration, boolean grown, String plantType) {
		String time = PersistentGrowth.formatTime(duration);
		String message = String.format(messageFormat, plantType, grown ? "grew in" : "will grow in", time);
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
