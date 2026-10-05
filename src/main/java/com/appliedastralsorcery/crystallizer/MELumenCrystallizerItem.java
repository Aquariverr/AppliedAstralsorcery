package com.appliedastralsorcery.crystallizer;

import appeng.items.parts.PartItem;

public final class MELumenCrystallizerItem extends PartItem<MELumenCrystallizerPart> {
    public MELumenCrystallizerItem() {
        super(new Properties(), MELumenCrystallizerPart.class, MELumenCrystallizerPart::new);
    }
}
