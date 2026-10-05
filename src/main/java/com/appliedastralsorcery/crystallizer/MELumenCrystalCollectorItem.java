package com.appliedastralsorcery.crystallizer;

import appeng.items.parts.PartItem;

/** A cable-mounted collector using AE2's native placement and preview. */
public final class MELumenCrystalCollectorItem extends PartItem<MELumenCrystalCollectorPart> {
    public MELumenCrystalCollectorItem() {
        super(new Properties(), MELumenCrystalCollectorPart.class, MELumenCrystalCollectorPart::new);
    }
}
