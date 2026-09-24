package org.ctrlaltdyleted.forgefrontierlostages.compat.curios;

import net.minecraftforge.common.MinecraftForge;

/** Kept separate so a dedicated server never loads screen classes. */
final class CuriosClientBootstrap {
    private CuriosClientBootstrap() {}
    static void initialize() {
        MinecraftForge.EVENT_BUS.register(new CuriosClientEvents());
    }
}
