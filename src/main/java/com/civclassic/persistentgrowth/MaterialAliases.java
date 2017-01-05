package com.civclassic.persistentgrowth;

import java.util.HashMap;
import java.util.Map;

import org.bukkit.Material;

public class MaterialAliases {
		// map Material that a user uses to hit the ground to a Material, TreeType,
		// or EntityType
		// that is specified. (ie, hit the ground with some wheat seeds and get a
		// message corresponding
		// to the wheat plant's growth rate
		private static Map<Material, Material> materialAliases = new HashMap<Material, Material>();

		static {
			materialAliases.put(Material.SEEDS, Material.CROPS);
			materialAliases.put(Material.WHEAT, Material.CROPS);
			materialAliases.put(Material.CARROT_ITEM, Material.CARROT);
			materialAliases.put(Material.POTATO_ITEM, Material.POTATO);
			materialAliases.put(Material.POISONOUS_POTATO, Material.POTATO);
			materialAliases.put(Material.BEETROOT_SEEDS, Material.BEETROOT_BLOCK);
			materialAliases.put(Material.BEETROOT_BLOCK, Material.BEETROOT_BLOCK);
			materialAliases.put(Material.BEETROOT, Material.BEETROOT_BLOCK);

			materialAliases.put(Material.MELON_SEEDS, Material.MELON_STEM);
			materialAliases.put(Material.MELON, Material.MELON_BLOCK);
			materialAliases.put(Material.MELON_BLOCK, Material.MELON_BLOCK);
			materialAliases.put(Material.PUMPKIN_SEEDS, Material.PUMPKIN_STEM);
			materialAliases.put(Material.PUMPKIN, Material.PUMPKIN);

			materialAliases.put(Material.INK_SACK, Material.COCOA);

			materialAliases.put(Material.CACTUS, Material.CACTUS);

			materialAliases.put(Material.SUGAR_CANE, Material.SUGAR_CANE_BLOCK);

			materialAliases.put(Material.NETHER_STALK, Material.NETHER_WARTS);
		}
		
		public static Material getBlockFromItem(Material material) {
			return materialAliases.get(material);
		}
}
