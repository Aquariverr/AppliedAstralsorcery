package com.appliedastralsorcery.crystallizer;

import appeng.items.parts.PartItem;

/** Uses AE2's placement, cable hosting and placement preview without a separate block item. */
public final class MELumenCrystallizerItem extends PartItem<MELumenCrystallizerPart> {
    public MELumenCrystallizerItem() {
        super(new Properties(), MELumenCrystallizerPart.class, MELumenCrystallizerPart::new);
    }
}
