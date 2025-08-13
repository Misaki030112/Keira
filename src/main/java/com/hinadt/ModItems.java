package com.hinadt;

import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;


public class ModItems {

    public static void initialize() {


        ItemGroupEvents.modifyEntriesEvent(ItemGroups.INGREDIENTS)
                .register((itemGroup) -> itemGroup.add(ModItems.SUSPICIOUS_SUBSTANCE));

    }



    public static Item register(Item item, String id) {

        // Create the identifier for the item.
        Identifier itemID = Identifier.of(ExampleMod.MOD_ID, id);

        // Register the item.
        // Return the registered item!
        return Registry.register(Registries.ITEM, itemID, item);
    }

    public static final Item SUSPICIOUS_SUBSTANCE = register(
            new Item(new Item.Settings()),
            "suspicious_substance"
    );


}
