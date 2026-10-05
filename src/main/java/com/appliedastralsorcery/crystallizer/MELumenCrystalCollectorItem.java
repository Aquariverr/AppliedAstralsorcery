package com.appliedastralsorcery.crystallizer;

import appeng.items.parts.PartItem;

public final class MELumenCrystalCollectorItem extends PartItem<MELumenCrystalCollectorPart> {
    public MELumenCrystalCollectorItem() {
        super(new Properties(), MELumenCrystalCollectorPart.class, MELumenCrystalCollectorPart::new);
    }
}
